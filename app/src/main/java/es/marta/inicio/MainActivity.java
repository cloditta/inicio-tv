package es.marta.inicio;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pantalla de inicio minimalista: fondo rotativo, dock de favoritas abajo
 * y una vista con todas las aplicaciones. Sin dependencias externas.
 */
public class MainActivity extends Activity {

    /** Cada cuanto cambia el fondo. */
    private static final long ROTAR_MS = 5 * 60 * 1000L;

    /** Orden del dock. Si una app no esta instalada, se omite. */
    private static final String[][] FAVORITAS = {
            {"Netflix", "com.netflix.ninja"},
            {"YouTube", "com.google.android.youtube.tv"},
            {"Prime Video", "com.amazon.amazonvideo.livingroom"},
            {"Disney+", "com.disney.disneyplus"},
            {"Movistar+", "com.movistarplus.androidtv"},
            {"Max", "com.wbd.stream"},
            {"RTVE Play", "com.rtve.androidtv"},
            {"Mitele", "tv.accedo.mitele_xdk"},
    };

    private static final String PAQUETE_TV = "com.mediatek.wwtv.tvcenter";
    private static final String PAQUETE_AJUSTES = "com.android.tv.settings";

    /** Degradados de respaldo con aire de naturaleza (si no hay fotos). */
    private static final int[][] DEGRADADOS = {
            {0xFF0B3D2E, 0xFF2E7D5B, 0xFF9BC53D},   // bosque
            {0xFF0A2540, 0xFF1F6F8B, 0xFF99D5C9},   // lago
            {0xFF2B1B3D, 0xFF8E4162, 0xFFF2A65A},   // atardecer
            {0xFF10261F, 0xFF3C6E57, 0xFFC7D9B7},   // niebla
            {0xFF1B2A41, 0xFF324A5F, 0xFFCCC9DC},   // montana
    };

    private PackageManager pm;
    private final Handler h = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private ImageView bgA;
    private ImageView bgB;
    private boolean frenteEsA = true;
    private boolean hayFondo = false;
    private int indiceFondo = 0;
    private int indiceDegradado = 0;

    private LinearLayout dock;
    private ScrollView overlay;
    private GridLayout rejilla;

    private final Runnable rotar = new Runnable() {
        @Override
        public void run() {
            siguienteFondo();
            h.postDelayed(this, ROTAR_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);

        bgA = vistaFondo();
        bgB = vistaFondo();
        bgB.setAlpha(0f);
        root.addView(bgA);
        root.addView(bgB);

        // Degradado oscuro inferior para que el dock se lea sobre cualquier foto.
        View velo = new View(this);
        velo.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xB3000000}));
        root.addView(velo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320), Gravity.BOTTOM));

        TextClock reloj = new TextClock(this);
        reloj.setFormat24Hour("HH:mm");
        reloj.setFormat12Hour("HH:mm");
        reloj.setTextColor(Color.WHITE);
        reloj.setTextSize(30);
        reloj.setShadowLayer(8, 0, 2, 0xAA000000);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        rp.setMargins(0, dp(36), dp(56), 0);
        root.addView(reloj, rp);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setClipChildren(false);
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        dockLp.setMargins(0, 0, 0, dp(44));
        root.addView(dock, dockLp);

        rejilla = new GridLayout(this);
        rejilla.setColumnCount(6);
        rejilla.setClipChildren(false);
        overlay = new ScrollView(this);
        overlay.setBackgroundColor(0xE6000000);
        overlay.setPadding(dp(72), dp(56), dp(72), dp(56));
        overlay.addView(rejilla);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        construirDock();
        h.removeCallbacks(rotar);
        if (!hayFondo) {
            siguienteFondo();
        }
        h.postDelayed(rotar, ROTAR_MS);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(rotar);
        super.onPause();
    }

    /** Se llama al pulsar Inicio estando ya en el launcher. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        cerrarApps();
    }

    /** Un launcher no debe cerrarse con Atras; solo cierra la vista de todas las apps. */
    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) {
            cerrarApps();
        }
    }

    // ------------------------------------------------------------------ dock

    private void construirDock() {
        dock.removeAllViews();
        for (final String[] f : FAVORITAS) {
            if (!instalada(f[1])) {
                continue;
            }
            final String pkg = f[1];
            dock.addView(item(f[0], icono(pkg), false, new Runnable() {
                @Override
                public void run() {
                    abrir(pkg);
                }
            }));
        }
        dock.addView(item("TV en directo", icono(PAQUETE_TV), false, new Runnable() {
            @Override
            public void run() {
                abrirTv();
            }
        }));
        dock.addView(item("Ajustes", icono(PAQUETE_AJUSTES), false, new Runnable() {
            @Override
            public void run() {
                startSeguro(new Intent(Settings.ACTION_SETTINGS));
            }
        }));
        dock.addView(item("Todas las apps", iconoApps(), false, new Runnable() {
            @Override
            public void run() {
                mostrarApps();
            }
        }));
        if (getCurrentFocus() == null && dock.getChildCount() > 0) {
            dock.getChildAt(0).requestFocus();
        }
    }

    private View item(String texto, Drawable icono, boolean etiquetaSiempre, final Runnable accion) {
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.setClipChildren(false);
        col.setPadding(dp(14), dp(12), dp(14), dp(10));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icono);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        col.addView(iv, new LinearLayout.LayoutParams(dp(88), dp(88)));

        final TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setShadowLayer(6, 0, 2, 0xCC000000);
        tv.setAlpha(etiquetaSiempre ? 1f : 0f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                dp(120), ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(8);
        col.addView(tv, tp);

        final GradientDrawable foco = new GradientDrawable();
        foco.setColor(0x33FFFFFF);
        foco.setCornerRadius(dp(20));
        final boolean siempre = etiquetaSiempre;

        col.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean tiene) {
                v.animate().scaleX(tiene ? 1.16f : 1f).scaleY(tiene ? 1.16f : 1f)
                        .setDuration(140).start();
                v.setBackground(tiene ? foco : null);
                if (!siempre) {
                    tv.animate().alpha(tiene ? 1f : 0f).setDuration(140).start();
                }
            }
        });
        col.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accion.run();
            }
        });
        return col;
    }

    // ------------------------------------------------------- todas las apps

    private void mostrarApps() {
        rejilla.removeAllViews();
        Set<String> vistos = new HashSet<String>();
        List<ResolveInfo> lista = new ArrayList<ResolveInfo>();
        String[] categorias = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categorias) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || !vistos.add(p)) {
                    continue;
                }
                lista.add(ri);
            }
        }
        Collections.sort(lista, new java.util.Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        for (ResolveInfo ri : lista) {
            final String pkg = ri.activityInfo.packageName;
            rejilla.addView(item(ri.loadLabel(pm).toString(), ri.loadIcon(pm), true, new Runnable() {
                @Override
                public void run() {
                    abrir(pkg);
                }
            }));
        }
        overlay.setVisibility(View.VISIBLE);
        overlay.scrollTo(0, 0);
        if (rejilla.getChildCount() > 0) {
            rejilla.getChildAt(0).requestFocus();
        }
    }

    private void cerrarApps() {
        if (overlay.getVisibility() == View.VISIBLE) {
            overlay.setVisibility(View.GONE);
            if (dock.getChildCount() > 0) {
                dock.getChildAt(0).requestFocus();
            }
        }
    }

    // ---------------------------------------------------------- lanzamiento

    private boolean instalada(String pkg) {
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void abrir(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) {
            i = pm.getLaunchIntentForPackage(pkg);
        }
        if (i == null) {
            Toast.makeText(this, "No se puede abrir " + pkg, Toast.LENGTH_SHORT).show();
            return;
        }
        startSeguro(i);
    }

    private void abrirTv() {
        Intent i = pm.getLeanbackLaunchIntentForPackage(PAQUETE_TV);
        if (i == null) {
            i = pm.getLaunchIntentForPackage(PAQUETE_TV);
        }
        if (i == null) {
            i = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI);
        }
        startSeguro(i);
    }

    private void startSeguro(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show();
        }
    }

    // --------------------------------------------------------------- fondos

    private ImageView vistaFondo() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return iv;
    }

    /**
     * Fotos en: /sdcard/Android/data/es.marta.inicio/files/fondos
     * Si la carpeta esta vacia se usan degradados.
     */
    private void siguienteFondo() {
        File dir = getExternalFilesDir("fondos");
        File[] fotos = null;
        if (dir != null) {
            fotos = dir.listFiles(new java.io.FilenameFilter() {
                @Override
                public boolean accept(File d, String n) {
                    String s = n.toLowerCase();
                    return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png");
                }
            });
        }
        if (fotos == null || fotos.length == 0) {
            mostrar(degradado());
            return;
        }
        Arrays.sort(fotos);
        final File f = fotos[indiceFondo++ % fotos.length];
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = cargar(f);
                if (b != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mostrar(new BitmapDrawable(getResources(), b));
                        }
                    });
                }
            }
        }).start();
    }

    private Bitmap cargar(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int s = 1;
        while (o.outWidth / (s * 2) >= 1920 && o.outHeight / (s * 2) >= 1080) {
            s *= 2;
        }
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private Drawable degradado() {
        int[] colores = DEGRADADOS[indiceDegradado++ % DEGRADADOS.length];
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, colores);
    }

    private void mostrar(Drawable d) {
        hayFondo = true;
        ImageView frente = frenteEsA ? bgA : bgB;
        ImageView atras = frenteEsA ? bgB : bgA;
        atras.setImageDrawable(d);
        atras.animate().alpha(1f).setDuration(1500).start();
        frente.animate().alpha(0f).setDuration(1500).start();
        frenteEsA = !frenteEsA;
    }

    // --------------------------------------------------------------- iconos

    private Drawable icono(String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return pm.getDefaultActivityIcon();
        }
    }

    private Drawable iconoApps() {
        int s = 256;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x55FFFFFF);
        c.drawRoundRect(new RectF(0, 0, s, s), 56, 56, p);
        p.setColor(Color.WHITE);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                c.drawCircle(64 + i * 64, 64 + j * 64, 16, p);
            }
        }
        return new BitmapDrawable(getResources(), b);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
