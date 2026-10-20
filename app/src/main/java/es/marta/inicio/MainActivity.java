package es.marta.inicio;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
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
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pantalla de inicio minimalista: fondo rotativo con zoom lento, saludo y hora,
 * dock traslucido con tarjetas de las apps y una vista con todas las aplicaciones.
 * Sin dependencias externas.
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

    private PackageManager pm;
    private final Handler h = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private ImageView bgA;
    private ImageView bgB;
    private final ObjectAnimator[] zoom = new ObjectAnimator[2];
    private boolean frenteEsA = true;
    private boolean hayFondo = false;
    private int indiceFondo = 0;
    private int indicePaisaje = 0;

    private TextView saludo;
    private LinearLayout dock;
    private HorizontalScrollView dockScroll;
    private TextView etiqueta;
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

        bgA = vistaFondo();
        bgB = vistaFondo();
        bgB.setAlpha(0f);
        root.addView(bgA);
        root.addView(bgB);

        // Sombreado arriba (para la hora) y abajo (para el dock).
        View velo = new View(this);
        velo.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xCC000000}));
        root.addView(velo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360), Gravity.BOTTOM));
        View veloArriba = new View(this);
        veloArriba.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x00000000}));
        root.addView(veloArriba, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240), Gravity.TOP));

        // Saludo, hora y fecha arriba a la izquierda.
        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setOrientation(LinearLayout.VERTICAL);

        saludo = new TextView(this);
        saludo.setTextColor(0xB3FFFFFF);
        saludo.setTextSize(13);
        saludo.setLetterSpacing(0.25f);
        saludo.setShadowLayer(6, 0, 2, 0x99000000);
        cabecera.addView(saludo);

        TextClock reloj = new TextClock(this);
        reloj.setFormat24Hour("HH:mm");
        reloj.setFormat12Hour("HH:mm");
        reloj.setTextColor(Color.WHITE);
        reloj.setTextSize(64);
        reloj.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        reloj.setIncludeFontPadding(false);
        reloj.setShadowLayer(10, 0, 3, 0x88000000);
        cabecera.addView(reloj);

        TextClock fecha = new TextClock(this);
        fecha.setFormat24Hour("EEEE, d 'de' MMMM");
        fecha.setFormat12Hour("EEEE, d 'de' MMMM");
        fecha.setTextColor(0xCCFFFFFF);
        fecha.setTextSize(18);
        fecha.setShadowLayer(6, 0, 2, 0x99000000);
        cabecera.addView(fecha);

        FrameLayout.LayoutParams cabeceraLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        cabeceraLp.setMargins(dp(72), dp(44), 0, 0);
        root.addView(cabecera, cabeceraLp);

        // Dock: barra traslucida con scroll horizontal.
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable barra = new GradientDrawable();
        barra.setColor(0x59101216);
        barra.setCornerRadius(dp(30));
        barra.setStroke(dp(1), 0x26FFFFFF);

        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setFillViewport(true);
        dockScroll.setBackground(barra);
        dockScroll.setClipToOutline(true);
        dockScroll.addView(dock, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        dockLp.setMargins(dp(56), 0, dp(56), dp(44));
        root.addView(dockScroll, dockLp);

        // Nombre de la app seleccionada, encima del dock y alineado a la izquierda.
        etiqueta = new TextView(this);
        etiqueta.setTextColor(Color.WHITE);
        etiqueta.setTextSize(26);
        etiqueta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        etiqueta.setShadowLayer(8, 0, 2, 0xCC000000);
        FrameLayout.LayoutParams etiquetaLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        etiquetaLp.setMargins(dp(84), 0, 0, dp(176));
        root.addView(etiqueta, etiquetaLp);

        // Vista de todas las apps.
        LinearLayout columna = new LinearLayout(this);
        columna.setOrientation(LinearLayout.VERTICAL);
        TextView titulo = new TextView(this);
        titulo.setText("Todas las apps");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(28);
        titulo.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        titulo.setPadding(dp(14), 0, 0, dp(18));
        columna.addView(titulo);
        rejilla = new GridLayout(this);
        rejilla.setColumnCount(5);
        columna.addView(rejilla);

        overlay = new ScrollView(this);
        overlay.setBackgroundColor(0xEE0E1014);
        overlay.setPadding(dp(64), dp(48), dp(64), dp(48));
        overlay.setClipToPadding(false);
        overlay.addView(columna);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualizarSaludo();
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

    private void actualizarSaludo() {
        int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String s;
        if (hora < 6) {
            s = "BUENAS NOCHES";
        } else if (hora < 13) {
            s = "BUENOS DÍAS";
        } else if (hora < 21) {
            s = "BUENAS TARDES";
        } else {
            s = "BUENAS NOCHES";
        }
        saludo.setText(s);
    }

    // ------------------------------------------------------------------ dock

    private void construirDock() {
        dock.removeAllViews();
        for (final String[] f : FAVORITAS) {
            if (!instalada(f[1])) {
                continue;
            }
            final String pkg = f[1];
            dock.addView(item(f[0], tileDe(pkg), false, new Runnable() {
                @Override
                public void run() {
                    abrir(pkg);
                }
            }));
        }
        dock.addView(item("TV en directo", panel(icono(PAQUETE_TV)), false, new Runnable() {
            @Override
            public void run() {
                abrirTv();
            }
        }));
        dock.addView(item("Ajustes", panel(icono(PAQUETE_AJUSTES)), false, new Runnable() {
            @Override
            public void run() {
                startSeguro(new Intent(Settings.ACTION_SETTINGS));
            }
        }));
        dock.addView(item("Todas las apps", tileApps(), false, new Runnable() {
            @Override
            public void run() {
                mostrarApps();
            }
        }));
        if (getCurrentFocus() == null && dock.getChildCount() > 0) {
            dock.getChildAt(0).requestFocus();
        }
    }

    /**
     * Tarjeta con esquinas redondeadas. En el dock el nombre se muestra encima de la barra
     * al seleccionarla; en la vista de todas las apps va siempre bajo la tarjeta.
     */
    private View item(final String texto, Drawable tile, final boolean grid, final Runnable accion) {
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.setPadding(dp(7), dp(10), dp(7), dp(10));

        int anchoTile = grid ? 136 : 128;
        int altoTile = grid ? 76 : 72;

        final FrameLayout caja = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(tile);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
            }
        });
        caja.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(caja, new LinearLayout.LayoutParams(dp(anchoTile), dp(altoTile)));

        if (grid) {
            TextView tv = new TextView(this);
            tv.setText(texto);
            tv.setTextColor(0xE6FFFFFF);
            tv.setTextSize(14);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    dp(anchoTile + 12), ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.topMargin = dp(8);
            col.addView(tv, tp);
        }

        // Aro blanco al seleccionar.
        final GradientDrawable aro = new GradientDrawable();
        aro.setColor(Color.TRANSPARENT);
        aro.setStroke(dp(3), Color.WHITE);
        aro.setCornerRadius(dp(14));

        col.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean tiene) {
                float escala = tiene ? (grid ? 1.1f : 1.08f) : 1f;
                v.animate().scaleX(escala).scaleY(escala).setDuration(140).start();
                caja.setForeground(tiene ? aro : null);
                if (!grid && tiene) {
                    etiqueta.setText(texto);
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
            rejilla.addView(item(ri.loadLabel(pm).toString(), tileDe(ri), true, new Runnable() {
                @Override
                public void run() {
                    abrir(pkg);
                }
            }));
        }
        overlay.setVisibility(View.VISIBLE);
        etiqueta.setVisibility(View.GONE);
        overlay.scrollTo(0, 0);
        if (rejilla.getChildCount() > 0) {
            rejilla.getChildAt(0).requestFocus();
        }
    }

    private void cerrarApps() {
        if (overlay.getVisibility() == View.VISIBLE) {
            overlay.setVisibility(View.GONE);
            etiqueta.setVisibility(View.VISIBLE);
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
     * Si la carpeta esta vacia se generan paisajes de montanas.
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
            final int estilo = indicePaisaje++ % Paisaje.ESTILOS;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = Paisaje.crear(1280, 720, estilo, System.nanoTime());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mostrar(new BitmapDrawable(getResources(), b));
                        }
                    });
                }
            }).start();
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

    /** Fundido entre fondos y zoom lento (efecto Ken Burns) sobre el que entra. */
    private void mostrar(Drawable d) {
        hayFondo = true;
        ImageView frente = frenteEsA ? bgA : bgB;
        ImageView atras = frenteEsA ? bgB : bgA;
        int idx = frenteEsA ? 1 : 0;
        if (zoom[idx] != null) {
            zoom[idx].cancel();
        }
        atras.setScaleX(1f);
        atras.setScaleY(1f);
        atras.setImageDrawable(d);
        atras.animate().alpha(1f).setDuration(1500).start();
        frente.animate().alpha(0f).setDuration(1500).start();

        ObjectAnimator z = ObjectAnimator.ofPropertyValuesHolder(atras,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f));
        z.setDuration(ROTAR_MS + 3000);
        z.setInterpolator(new LinearInterpolator());
        z.start();
        zoom[idx] = z;
        frenteEsA = !frenteEsA;
    }

    // --------------------------------------------------------------- tarjetas

    /** Banner de la app (como en el launcher de Android TV) o, si no tiene, su icono sobre un panel. */
    private Drawable tileDe(String pkg) {
        Drawable banner = null;
        try {
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li != null) {
                banner = pm.getActivityBanner(li);
            }
            if (banner == null) {
                banner = pm.getApplicationBanner(pkg);
            }
        } catch (Exception e) {
            banner = null;
        }
        return banner != null ? banner : panel(icono(pkg));
    }

    private Drawable tileDe(ResolveInfo ri) {
        Drawable banner = ri.activityInfo.loadBanner(pm);
        return banner != null ? banner : panel(ri.loadIcon(pm));
    }

    private Drawable icono(String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return pm.getDefaultActivityIcon();
        }
    }

    /** Fondo oscuro 16:9 con el icono en el centro. */
    private Drawable panel(Drawable icono) {
        int w = 320;
        int hh = 180;
        Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, hh, 0xFF3A3F4B, 0xFF1E2128,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, hh, p);
        int s = 96;
        int l = (w - s) / 2;
        int t = (hh - s) / 2;
        icono.setBounds(l, t, l + s, t + s);
        icono.draw(c);
        return new BitmapDrawable(getResources(), b);
    }

    /** Tarjeta de "Todas las apps": nueve puntos sobre el mismo panel. */
    private Drawable tileApps() {
        int w = 320;
        int hh = 180;
        Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, hh, 0xFF3A3F4B, 0xFF1E2128,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, hh, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                c.drawCircle(w / 2f - 40 + i * 40, hh / 2f - 40 + j * 40, 11, p);
            }
        }
        return new BitmapDrawable(getResources(), b);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
