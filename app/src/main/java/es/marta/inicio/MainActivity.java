package es.marta.inicio;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Context;
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
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final long ROTAR_MS = 5 * 60 * 1000L; // 5 minutos

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
    private final OvershootInterpolator rebote = new OvershootInterpolator(1.1f);
    private final DecelerateInterpolator suave = new DecelerateInterpolator(1.5f);

    private FrameLayout root;
    private ImageView bgA;
    private ImageView bgB;
    private final ObjectAnimator[] zoom = new ObjectAnimator[2];
    private boolean frenteEsA = true;
    private boolean hayFondo = false;
    private int indiceFondo = 0;
    private int indicePaisaje = 0;

    private LinearLayout dock;
    private HorizontalScrollView dockScroll;
    private TextView etiqueta;
    private ScrollView overlay;
    private GridLayout rejilla;
    private FrameLayout overlayHdmi;
    private LinearLayout contenedorHdmi;

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

        // Velo inferior sutil para dar contraste limpio al dock estilo tvOS
        View velo = new View(this);
        velo.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x4D000000, 0x99000000}));
        root.addView(velo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320), Gravity.BOTTOM));

        // Velo superior ligero
        View veloArriba = new View(this);
        veloArriba.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x00000000}));
        root.addView(veloArriba, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(160), Gravity.TOP));

        // --- CABECERA MINIMALISTA ESTILO APPLE TV (Arriba a la derecha) ---
        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setOrientation(LinearLayout.VERTICAL);
        cabecera.setGravity(Gravity.END);

        TextClock reloj = new TextClock(this);
        reloj.setFormat24Hour("HH:mm");
        reloj.setFormat12Hour("HH:mm");
        reloj.setTextColor(0xF2FFFFFF);
        reloj.setTextSize(44);
        reloj.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        reloj.setIncludeFontPadding(false);
        reloj.setShadowLayer(12, 0, 3, 0x66000000);
        cabecera.addView(reloj);

        TextClock fecha = new TextClock(this);
        fecha.setFormat24Hour("EEEE, d MMM");
        fecha.setFormat12Hour("EEEE, d MMM");
        fecha.setTextColor(0xB3FFFFFF);
        fecha.setTextSize(14);
        fecha.setLetterSpacing(0.02f);
        fecha.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        fecha.setShadowLayer(8, 0, 2, 0x66000000);
        cabecera.addView(fecha);

        FrameLayout.LayoutParams cabeceraLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        cabeceraLp.setMargins(0, dp(40), dp(58), 0);
        root.addView(cabecera, cabeceraLp);

        // --- DOCK CRISTAL TRASLÚCIDO (tvOS Frosted Glass) ---
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(26), dp(22), dp(26), dp(22));
        dock.setClipChildren(false);
        dock.setClipToPadding(false);

        GradientDrawable barra = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33FFFFFF, 0x1AFFFFFF});
        barra.setCornerRadius(dp(38));
        barra.setStroke(dp(1), 0x40FFFFFF);

        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setFillViewport(false);
        dockScroll.setBackground(barra);
        dockScroll.setClipToOutline(true);
        dockScroll.setClipChildren(false);
        dockScroll.setClipToPadding(false);
        dockScroll.setElevation(dp(20));
        dockScroll.addView(dock, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockLp.setMargins(dp(48), 0, dp(48), dp(38));
        root.addView(dockScroll, dockLp);

        // --- ETIQUETA DE APP LIMPIA ---
        etiqueta = new TextView(this);
        etiqueta.setTextColor(Color.WHITE);
        etiqueta.setTextSize(20);
        etiqueta.setLetterSpacing(0.01f);
        etiqueta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        etiqueta.setShadowLayer(10, 0, 3, 0x99000000);
        FrameLayout.LayoutParams etiquetaLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        etiquetaLp.setMargins(0, 0, 0, dp(192));
        root.addView(etiqueta, etiquetaLp);

        // --- CAJÓN DE APLICACIONES ESTILO tvOS ---
        LinearLayout columna = new LinearLayout(this);
        columna.setOrientation(LinearLayout.VERTICAL);
        columna.setGravity(Gravity.CENTER_HORIZONTAL);
        columna.setClipChildren(false);
        columna.setClipToPadding(false);

        TextView titulo = new TextView(this);
        titulo.setText("Aplicaciones");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(26);
        titulo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titulo.setPadding(0, 0, 0, dp(28));
        columna.addView(titulo);

        rejilla = new GridLayout(this);
        rejilla.setColumnCount(5);
        rejilla.setClipChildren(false);
        rejilla.setClipToPadding(false);
        columna.addView(rejilla);

        overlay = new ScrollView(this);
        overlay.setBackgroundColor(0xE6121317);
        overlay.setPadding(dp(56), dp(48), dp(56), dp(48));
        overlay.setClipToPadding(false);
        overlay.addView(columna);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // --- SELECTOR HDMI MINIMALISTA ---
        construirOverlayHdmi();

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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        cerrarModales();
    }

    @Override
    public void onBackPressed() {
        if (overlayHdmi != null && overlayHdmi.getVisibility() == View.VISIBLE) {
            cerrarHdmi();
        } else if (overlay.getVisibility() == View.VISIBLE) {
            cerrarApps();
        }
    }

    private void cerrarModales() {
        cerrarHdmi();
        cerrarApps();
    }

    private void construirDock() {
        dock.removeAllViews();

        dock.addView(item("TV en directo", tileDe(PAQUETE_TV), false, new Runnable() {
            @Override
            public void run() { abrirTv(); }
        }));

        dock.addView(item("Entradas HDMI", tileHdmi(), false, new Runnable() {
            @Override
            public void run() { mostrarSelectorHdmi(); }
        }));

        for (final String[] f : FAVORITAS) {
            if (!instalada(f[1])) continue;
            final String pkg = f[1];
            dock.addView(item(f[0], tileDe(pkg), false, new Runnable() {
                @Override
                public void run() { abrir(pkg); }
            }));
        }

        dock.addView(item("Todas las apps", tileApps(), false, new Runnable() {
            @Override
            public void run() { mostrarApps(); }
        }));

        dock.addView(item("Ajustes", panel(icono(PAQUETE_AJUSTES)), false, new Runnable() {
            @Override
            public void run() { startSeguro(new Intent(Settings.ACTION_SETTINGS)); }
        }));

        if (getCurrentFocus() == null && dock.getChildCount() > 0) {
            dock.getChildAt(0).requestFocus();
        }
    }

    // --- PANEL MODAL DE ENTRADAS HDMI ESTILO APPLE TV ---
    private void construirOverlayHdmi() {
        overlayHdmi = new FrameLayout(this);
        overlayHdmi.setBackgroundColor(0xB3000000);
        overlayHdmi.setVisibility(View.GONE);

        LinearLayout tarjeta = new LinearLayout(this);
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        tarjeta.setGravity(Gravity.CENTER_HORIZONTAL);
        tarjeta.setPadding(dp(44), dp(36), dp(44), dp(40));
        tarjeta.setClipChildren(false);
        tarjeta.setClipToPadding(false);

        // Cristal translúcido estilo tvOS
        GradientDrawable fondoModal = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xEE26282E, 0xEE181A1F});
        fondoModal.setCornerRadius(dp(32));
        fondoModal.setStroke(dp(1), 0x38FFFFFF);
        tarjeta.setBackground(fondoModal);
        tarjeta.setElevation(dp(24));

        TextView tit = new TextView(this);
        tit.setText("Entradas de vídeo");
        tit.setTextColor(Color.WHITE);
        tit.setTextSize(22);
        tit.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titLp.bottomMargin = dp(26);
        tarjeta.addView(tit, titLp);

        contenedorHdmi = new LinearLayout(this);
        contenedorHdmi.setOrientation(LinearLayout.HORIZONTAL);
        contenedorHdmi.setGravity(Gravity.CENTER);
        contenedorHdmi.setClipChildren(false);
        contenedorHdmi.setClipToPadding(false);
        tarjeta.addView(contenedorHdmi);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        overlayHdmi.addView(tarjeta, lp);
        root.addView(overlayHdmi, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void mostrarSelectorHdmi() {
        contenedorHdmi.removeAllViews();

        contenedorHdmi.addView(crearTarjetaPuerto("TV", "Antena", true, new Runnable() {
            @Override
            public void run() {
                cerrarHdmi();
                abrirTv();
            }
        }));

        boolean encontrados = false;
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                List<TvInputInfo> inputs = tim.getTvInputList();
                List<TvInputInfo> hdmis = new ArrayList<TvInputInfo>();
                for (TvInputInfo info : inputs) {
                    if (info.getType() == TvInputInfo.TYPE_HDMI || info.getId().toLowerCase().contains("hdmi")) {
                        hdmis.add(info);
                    }
                }
                Collections.sort(hdmis, new Comparator<TvInputInfo>() {
                    @Override
                    public int compare(TvInputInfo a, TvInputInfo b) {
                        return a.loadLabel(MainActivity.this).toString()
                                .compareToIgnoreCase(b.loadLabel(MainActivity.this).toString());
                    }
                });

                for (final TvInputInfo info : hdmis) {
                    encontrados = true;
                    CharSequence label = info.loadLabel(this);
                    String nombre = (label != null && label.length() > 0) ? label.toString() : "HDMI";
                    int estado = tim.getInputState(info.getId());
                    boolean activo = (estado == TvInputManager.INPUT_STATE_CONNECTED);
                    String sub = activo ? "Conectado" : "Disponible";

                    contenedorHdmi.addView(crearTarjetaPuerto(nombre, sub, activo, new Runnable() {
                        @Override
                        public void run() {
                            cerrarHdmi();
                            abrirInputId(info.getId());
                        }
                    }));
                }
            }
        } catch (Exception ignored) {}

        if (!encontrados) {
            for (int i = 1; i <= 3; i++) {
                final int num = i;
                contenedorHdmi.addView(crearTarjetaPuerto("HDMI " + num, "Entrada " + num, false, new Runnable() {
                    @Override
                    public void run() {
                        cerrarHdmi();
                        abrirHdmiFallback(num);
                    }
                }));
            }
        }

        overlayHdmi.setAlpha(0f);
        overlayHdmi.setVisibility(View.VISIBLE);
        overlayHdmi.animate().alpha(1f).setDuration(200).setInterpolator(suave).start();
        etiqueta.setVisibility(View.GONE);
        dockScroll.setVisibility(View.GONE);

        if (contenedorHdmi.getChildCount() > 1) {
            contenedorHdmi.getChildAt(1).requestFocus();
        } else if (contenedorHdmi.getChildCount() > 0) {
            contenedorHdmi.getChildAt(0).requestFocus();
        }
    }

    private View crearTarjetaPuerto(String titulo, String subtitulo, boolean conectado, final Runnable accion) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(20), dp(20), dp(20), dp(18));

        final GradientDrawable bgNormal = new GradientDrawable();
        bgNormal.setColor(0x22FFFFFF);
        bgNormal.setCornerRadius(dp(22));
        bgNormal.setStroke(dp(1), 0x26FFFFFF);

        final GradientDrawable bgFoco = new GradientDrawable();
        bgFoco.setColor(0xF2FFFFFF);
        bgFoco.setCornerRadius(dp(22));

        card.setBackground(bgNormal);
        card.setElevation(dp(6));

        final ImageView icono = new ImageView(this);
        final Drawable iconBlanco = dibujarIconoConector(Color.WHITE);
        final Drawable iconOscuro = dibujarIconoConector(0xFF141519);
        icono.setImageDrawable(iconBlanco);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(54), dp(34));
        iconLp.bottomMargin = dp(10);
        card.addView(icono, iconLp);

        final TextView tvTit = new TextView(this);
        tvTit.setText(titulo);
        tvTit.setTextColor(Color.WHITE);
        tvTit.setTextSize(16);
        tvTit.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvTit.setSingleLine(true);
        card.addView(tvTit);

        final TextView tvSub = new TextView(this);
        tvSub.setText(subtitulo);
        tvSub.setTextColor(conectado ? 0xCCFFFFFF : 0x80FFFFFF);
        tvSub.setTextSize(12);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        card.addView(tvSub, subLp);

        // En tvOS, la tarjeta seleccionada se ilumina en blanco puro con texto oscuro
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean tiene) {
                float s = tiene ? 1.12f : 1.0f;
                v.animate().scaleX(s).scaleY(s).translationZ(tiene ? dp(16) : 0f)
                        .setInterpolator(rebote).setDuration(240).start();
                card.setBackground(tiene ? bgFoco : bgNormal);
                icono.setImageDrawable(tiene ? iconOscuro : iconBlanco);
                tvTit.setTextColor(tiene ? 0xFF141519 : Color.WHITE);
                tvSub.setTextColor(tiene ? 0x99141519 : 0x99FFFFFF);
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { accion.run(); }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(146), dp(136));
        lp.setMargins(dp(10), dp(6), dp(10), dp(6));
        card.setLayoutParams(lp);
        return card;
    }

    private void abrirInputId(String inputId) {
        try {
            Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Exception ignored) {}

        try {
            Intent i = pm.getLeanbackLaunchIntentForPackage(PAQUETE_TV);
            if (i == null) i = pm.getLaunchIntentForPackage(PAQUETE_TV);
            if (i != null) {
                i.putExtra(TvInputInfo.EXTRA_INPUT_ID, inputId);
                startSeguro(i);
                return;
            }
        } catch (Exception ignored) {}

        abrirTv();
    }

    private void abrirHdmiFallback(int puerto) {
        String[] posiblesIds = {
                "com.mediatek.tvinput/.hdmi.HDMIInputService/HW" + (puerto + 4),
                "com.mediatek.tvinput/.hdmi.HDMIInputService/HW" + puerto,
                "com.droidlogic.tvinput/.services.Hdmi" + puerto + "InputService/HW" + (puerto + 4)
        };
        for (String id : posiblesIds) {
            try {
                Uri uri = TvContract.buildChannelUriForPassthroughInput(id);
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception ignored) {}
        }
        abrirTv();
    }

    private void cerrarHdmi() {
        if (overlayHdmi != null && overlayHdmi.getVisibility() == View.VISIBLE) {
            overlayHdmi.setVisibility(View.GONE);
            etiqueta.setVisibility(View.VISIBLE);
            dockScroll.setVisibility(View.VISIBLE);
            if (dock.getChildCount() > 1) dock.getChildAt(1).requestFocus();
        }
    }

    private View item(final String texto, Drawable tile, final boolean grid, final Runnable accion) {
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dp(10), dp(8), dp(10), dp(8));

        int anchoTile = grid ? 156 : 162;
        int altoTile = grid ? 88 : 92;
        final int radio = dp(22);

        final FrameLayout caja = new FrameLayout(this);
        caja.setElevation(dp(6));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            caja.setOutlineAmbientShadowColor(0x99000000);
            caja.setOutlineSpotShadowColor(0xCC000000);
        }
        caja.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radio);
            }
        });
        caja.setClipToOutline(true);

        final ImageView iv = new ImageView(this);
        iv.setImageDrawable(tile);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setAlpha(0.92f);
        caja.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(caja, new LinearLayout.LayoutParams(dp(anchoTile), dp(altoTile)));

        final TextView tvGrid;
        if (grid) {
            tvGrid = new TextView(this);
            tvGrid.setText(texto);
            tvGrid.setTextColor(0x99FFFFFF);
            tvGrid.setTextSize(13);
            tvGrid.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tvGrid.setGravity(Gravity.CENTER);
            tvGrid.setSingleLine(true);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    dp(anchoTile + 12), ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.topMargin = dp(10);
            col.addView(tvGrid, tp);
        } else {
            tvGrid = null;
        }

        final GradientDrawable aro = new GradientDrawable();
        aro.setColor(Color.TRANSPARENT);
        aro.setStroke(dp(3), Color.WHITE);
        aro.setCornerRadius(radio);

        col.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean tiene) {
                float escala = tiene ? (grid ? 1.12f : 1.16f) : 1f;
                v.animate()
                        .scaleX(escala)
                        .scaleY(escala)
                        .translationY(tiene && !grid ? dp(-6) : 0f)
                        .translationZ(tiene ? dp(20) : 0f)
                        .setInterpolator(rebote)
                        .setDuration(260)
                        .start();
                iv.setAlpha(tiene ? 1f : 0.92f);
                caja.setElevation(tiene ? dp(22) : dp(6));
                caja.setForeground(tiene ? aro : null);
                if (tvGrid != null) {
                    tvGrid.setTextColor(tiene ? Color.WHITE : 0x99FFFFFF);
                }
                if (!grid && tiene) {
                    etiqueta.setText(texto);
                }
            }
        });
        col.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { accion.run(); }
        });
        return col;
    }

    private void mostrarApps() {
        rejilla.removeAllViews();
        Set<String> vistos = new HashSet<String>();
        List<ResolveInfo> lista = new ArrayList<ResolveInfo>();
        String[] categorias = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categorias) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || !vistos.add(p)) continue;
                lista.add(ri);
            }
        }
        Collections.sort(lista, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        for (ResolveInfo ri : lista) {
            final String pkg = ri.activityInfo.packageName;
            rejilla.addView(item(ri.loadLabel(pm).toString(), tileDe(ri), true, new Runnable() {
                @Override
                public void run() { abrir(pkg); }
            }));
        }
        overlay.setAlpha(0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.animate().alpha(1f).setDuration(200).start();
        etiqueta.setVisibility(View.GONE);
        dockScroll.setVisibility(View.GONE);
        overlay.scrollTo(0, 0);
        if (rejilla.getChildCount() > 0) rejilla.getChildAt(0).requestFocus();
    }

    private void cerrarApps() {
        if (overlay.getVisibility() == View.VISIBLE) {
            overlay.setVisibility(View.GONE);
            etiqueta.setVisibility(View.VISIBLE);
            dockScroll.setVisibility(View.VISIBLE);
            if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
        }
    }

    private boolean instalada(String pkg) {
        try { pm.getApplicationInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private void abrir(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) { Toast.makeText(this, "No se puede abrir " + pkg, Toast.LENGTH_SHORT).show(); return; }
        startSeguro(i);
    }

    private void abrirTv() {
        Intent i = pm.getLeanbackLaunchIntentForPackage(PAQUETE_TV);
        if (i == null) i = pm.getLaunchIntentForPackage(PAQUETE_TV);
        if (i == null) i = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI);
        startSeguro(i);
    }

    private void startSeguro(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show(); }
    }

    private ImageView vistaFondo() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return iv;
    }

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
                        public void run() { mostrar(new BitmapDrawable(getResources(), b)); }
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
                        public void run() { mostrar(new BitmapDrawable(getResources(), b)); }
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
        while (o.outWidth / (s * 2) >= 1920 && o.outHeight / (s * 2) >= 1080) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private void mostrar(Drawable d) {
        hayFondo = true;
        ImageView frente = frenteEsA ? bgA : bgB;
        ImageView atras = frenteEsA ? bgB : bgA;
        int idx = frenteEsA ? 1 : 0;
        if (zoom[idx] != null) zoom[idx].cancel();

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

    private Drawable tileDe(String pkg) {
        Drawable banner = null;
        try {
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li != null) banner = pm.getActivityBanner(li);
            if (banner == null) banner = pm.getApplicationBanner(pkg);
        } catch (Exception e) { banner = null; }
        return banner != null ? banner : panel(icono(pkg));
    }

    private Drawable tileDe(ResolveInfo ri) {
        Drawable banner = ri.activityInfo.loadBanner(pm);
        return banner != null ? banner : panel(ri.loadIcon(pm));
    }

    private Drawable icono(String pkg) {
        try { return pm.getApplicationIcon(pkg); }
        catch (PackageManager.NameNotFoundException e) { return pm.getDefaultActivityIcon(); }
    }

    // Fondo gris neutro suave estilo Apple TV para tarjetas de sistema
    private void pintarFondoTvOS(Canvas c, int w, int hh) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, hh, 0xFF3A3D45, 0xFF22242A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, hh, p);
    }

    private Drawable panel(Drawable icono) {
        int w = 320; int hh = 180;
        Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        pintarFondoTvOS(c, w, hh);
        int s = 90; int l = (w - s) / 2; int t = (hh - s) / 2;
        icono.setBounds(l, t, l + s, t + s);
        icono.draw(c);
        return new BitmapDrawable(getResources(), b);
    }

    private Drawable tileHdmi() {
        int w = 320; int hh = 180;
        Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        pintarFondoTvOS(c, w, hh);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xF2FFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5.5f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);

        float cx = w / 2f;
        float cy = hh / 2f - 8;
        Path path = new Path();
        path.moveTo(cx - 42, cy - 16);
        path.lineTo(cx + 42, cy - 16);
        path.lineTo(cx + 42, cy + 4);
        path.lineTo(cx + 28, cy + 17);
        path.lineTo(cx - 28, cy + 17);
        path.lineTo(cx - 42, cy + 4);
        path.close();
        c.drawPath(path, p);

        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(cx - 22, cy - 2, cx + 22, cy + 3, 2, 2, p);

        p.setTextSize(18f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(0.18f);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.drawText("HDMI", cx, cy + 46, p);

        return new BitmapDrawable(getResources(), b);
    }

    private Drawable dibujarIconoConector(int color) {
        int w = 120; int hh = 76;
        Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4.5f);
        p.setStrokeJoin(Paint.Join.ROUND);

        float cx = w / 2f;
        float cy = hh / 2f;
        Path path = new Path();
        path.moveTo(cx - 32, cy - 13);
        path.lineTo(cx + 32, cy - 13);
        path.lineTo(cx + 32, cy + 3);
        path.lineTo(cx + 20, cy + 14);
        path.lineTo(cx - 20, cy + 14);
        path.lineTo(cx - 32, cy + 3);
        path.close();
        c.drawPath(path, p);

        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(cx - 16, cy - 2, cx + 16, cy + 3, 2, 2, p);
        return new BitmapDrawable(getResources(), b);
    }

    private Drawable tileApps() {
        int w = 320; int hh = 180;
        Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        pintarFondoTvOS(c, w, hh);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xF2FFFFFF);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                float left = w / 2f - 46 + i * 34;
                float top = hh / 2f - 22 + j * 28;
                c.drawRoundRect(left, top, left + 24, top + 16, 6, 6, p);
            }
        }
        return new BitmapDrawable(getResources(), b);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
