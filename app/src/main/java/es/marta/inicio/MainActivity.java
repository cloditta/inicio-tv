package es.marta.inicio;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.TransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final long ROTAR_MS = 5 * 60 * 1000L; // 5 minutos

    // Oscuridad que se pone sobre el fondo (0x00 = nada, 0xFF = negro total)
    private static final int SCRIM = 0xC8000000;
    private static final int COLOR_NEUTRO = 0xFF2E2F3A;

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

    // Brillo de color que cambia según la app seleccionada
    private View glow;
    private GradientDrawable glowDrawable;
    private int glowActual = 0xFF4A1414;
    private ValueAnimator glowAnim;

    // Pantalla principal
    private FrameLayout principal;
    private TextView saludo;
    private TextView tvRed;
    private TextView titulo;
    private ScrollView contenido;
    private HorizontalScrollView scrollFavs;
    private LinearLayout filaFavs;
    private LinearLayout filaApps;

    // Selector HDMI
    private FrameLayout overlayHdmi;
    private LinearLayout contenedorHdmi;

    private final Runnable rotar = new Runnable() {
        @Override
        public void run() {
            siguienteFondo();
            h.postDelayed(this, ROTAR_MS);
        }
    };

    private final Runnable refrescarSaludo = new Runnable() {
        @Override
        public void run() {
            saludo.setText(saludoActual());
            h.postDelayed(this, 60 * 1000L);
        }
    };

    /** Pasa el texto a mayúsculas (para la fecha, como en las capturas). */
    private static class Mayusculas implements TransformationMethod {
        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            return source == null ? null : source.toString().toUpperCase(Locale.getDefault());
        }

        @Override
        public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                                   int direction, Rect previouslyFocusedRect) {
        }
    }

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

        // Oscurece el fondo para que parezca el estilo de las capturas
        View scrim = new View(this);
        scrim.setBackgroundColor(SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Brillo radial de color detrás del contenido
        glowDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0, 0});
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(dp(640));
        glowDrawable.setGradientCenter(0.3f, 0.55f);
        aplicarGlow(glowActual);
        glow = new View(this);
        glow.setBackground(glowDrawable);
        root.addView(glow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        principal = new FrameLayout(this);
        principal.setClipChildren(false);
        principal.setClipToPadding(false);

        construirCabecera();
        construirBotonesSuperiores();
        construirContenidoBase();

        root.addView(principal, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        construirOverlayHdmi();

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        construirContenido();
        tvRed.setText(nombreRed());

        h.removeCallbacks(rotar);
        h.removeCallbacks(refrescarSaludo);
        if (!hayFondo) {
            siguienteFondo();
        }
        h.postDelayed(rotar, ROTAR_MS);
        h.post(refrescarSaludo);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(rotar);
        h.removeCallbacks(refrescarSaludo);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        cerrarHdmi();
        contenido.scrollTo(0, 0);
        scrollFavs.scrollTo(0, 0);
        if (filaFavs.getChildCount() > 0) filaFavs.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (overlayHdmi != null && overlayHdmi.getVisibility() == View.VISIBLE) {
            cerrarHdmi();
        }
    }

    // ------------------------------------------------------------------
    // CABECERA: saludo + reloj + fecha (arriba a la izquierda)
    // ------------------------------------------------------------------
    private void construirCabecera() {
        LinearLayout cab = new LinearLayout(this);
        cab.setOrientation(LinearLayout.VERTICAL);

        saludo = new TextView(this);
        saludo.setText(saludoActual());
        saludo.setTextColor(0xB3FFFFFF);
        saludo.setTextSize(12);
        saludo.setLetterSpacing(0.2f);
        saludo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cab.addView(saludo);

        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);

        TextClock reloj = new TextClock(this);
        reloj.setFormat24Hour("HH:mm");
        reloj.setFormat12Hour("HH:mm");
        reloj.setTextColor(Color.WHITE);
        reloj.setTextSize(54);
        reloj.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        reloj.setIncludeFontPadding(false);
        reloj.setShadowLayer(12, 0, 3, 0x66000000);
        fila.addView(reloj);

        View separador = new View(this);
        separador.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(dp(1), dp(34));
        sepLp.setMargins(dp(18), 0, dp(18), 0);
        fila.addView(separador, sepLp);

        TextClock fecha = new TextClock(this);
        fecha.setFormat24Hour("EEEE  d MMMM");
        fecha.setFormat12Hour("EEEE  d MMMM");
        fecha.setTransformationMethod(new Mayusculas());
        fecha.setTextColor(0xB3FFFFFF);
        fecha.setTextSize(15);
        fecha.setLetterSpacing(0.12f);
        fecha.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        fila.addView(fecha);

        cab.addView(fila);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        lp.setMargins(dp(48), dp(26), 0, 0);
        principal.addView(cab, lp);
    }

    private String saludoActual() {
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hr >= 6 && hr < 13) return "BUENOS DÍAS";
        if (hr >= 13 && hr < 21) return "BUENAS TARDES";
        return "BUENAS NOCHES";
    }

    // ------------------------------------------------------------------
    // BOTONES SUPERIORES: Entradas · Red · Ajustes (arriba a la derecha)
    // ------------------------------------------------------------------
    private void construirBotonesSuperiores() {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setClipChildren(false);
        fila.setClipToPadding(false);

        fila.addView(pill("Entradas", 0, new Runnable() {
            @Override
            public void run() { mostrarSelectorHdmi(); }
        }));

        View botonRed = pill("Red", 1, new Runnable() {
            @Override
            public void run() { startSeguro(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        });
        tvRed = (TextView) ((LinearLayout) botonRed).getChildAt(1);
        fila.addView(botonRed);

        fila.addView(pill("Ajustes", 2, new Runnable() {
            @Override
            public void run() { startSeguro(new Intent(Settings.ACTION_SETTINGS)); }
        }));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(28), dp(48), 0);
        principal.addView(fila, lp);
    }

    private View pill(String texto, final int tipoIcono, final Runnable accion) {
        final LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setFocusable(true);
        p.setClickable(true);
        p.setPadding(dp(18), dp(10), dp(20), dp(10));

        final GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x66000000);
        normal.setCornerRadius(dp(28));
        normal.setStroke(dp(1), 0x33FFFFFF);

        final GradientDrawable foco = new GradientDrawable();
        foco.setColor(0xF2FFFFFF);
        foco.setCornerRadius(dp(28));

        p.setBackground(normal);

        final Drawable iconoBlanco = iconoPill(tipoIcono, Color.WHITE);
        final Drawable iconoOscuro = iconoPill(tipoIcono, 0xFF141519);

        final ImageView iv = new ImageView(this);
        iv.setImageDrawable(iconoBlanco);
        int iw = tipoIcono == 0 ? 24 : 20;
        int ih = tipoIcono == 0 ? 15 : 20;
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(iw), dp(ih));
        ivLp.rightMargin = dp(10);
        p.addView(iv, ivLp);

        final TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setSingleLine(true);
        p.addView(tv);

        p.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean tiene) {
                float s = tiene ? 1.06f : 1f;
                v.animate().scaleX(s).scaleY(s).setInterpolator(rebote).setDuration(220).start();
                p.setBackground(tiene ? foco : normal);
                iv.setImageDrawable(tiene ? iconoOscuro : iconoBlanco);
                tv.setTextColor(tiene ? 0xFF141519 : Color.WHITE);
            }
        });
        p.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { accion.run(); }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(12);
        p.setLayoutParams(lp);
        return p;
    }

    private Drawable iconoPill(int tipo, int color) {
        if (tipo == 0) return dibujarIconoConector(color);
        if (tipo == 1) return dibujarIconoWifi(color);
        return dibujarIconoEngranaje(color);
    }

    private String nombreRed() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return "Sin red";
            if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) return "Ethernet";
            if (ni.getType() == ConnectivityManager.TYPE_WIFI) return "Wi-Fi";
            return ni.getTypeName();
        } catch (Exception e) {
            // Sin el permiso ACCESS_NETWORK_STATE no se puede leer el estado
            return "Red";
        }
    }

    // ------------------------------------------------------------------
    // CONTENIDO: título de la app + fila de favoritas + fila de todas
    // ------------------------------------------------------------------
    private void construirContenidoBase() {
        LinearLayout columna = new LinearLayout(this);
        columna.setOrientation(LinearLayout.VERTICAL);
        columna.setClipChildren(false);
        columna.setClipToPadding(false);
        columna.setPadding(dp(48), 0, dp(48), dp(40));

        titulo = new TextView(this);
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(40);
        titulo.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        titulo.setSingleLine(true);
        titulo.setShadowLayer(10, 0, 3, 0x66000000);
        titulo.setPadding(dp(12), 0, 0, dp(12));
        columna.addView(titulo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Fila 1: favoritas dentro de un panel oscuro redondeado
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xF2131417);
        panel.setCornerRadius(dp(24));
        panel.setStroke(dp(1), 0x1FFFFFFF);

        filaFavs = new LinearLayout(this);
        filaFavs.setOrientation(LinearLayout.HORIZONTAL);
        filaFavs.setClipChildren(false);
        filaFavs.setClipToPadding(false);

        scrollFavs = new HorizontalScrollView(this);
        scrollFavs.setHorizontalScrollBarEnabled(false);
        scrollFavs.setBackground(panel);
        scrollFavs.setPadding(dp(14), dp(16), dp(14), dp(16));
        scrollFavs.setClipChildren(false);
        scrollFavs.setClipToPadding(false);
        scrollFavs.addView(filaFavs, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        columna.addView(scrollFavs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Fila 2: todas las demás apps, sin panel
        filaApps = new LinearLayout(this);
        filaApps.setOrientation(LinearLayout.HORIZONTAL);
        filaApps.setClipChildren(false);
        filaApps.setClipToPadding(false);

        HorizontalScrollView scrollApps = new HorizontalScrollView(this);
        scrollApps.setHorizontalScrollBarEnabled(false);
        scrollApps.setPadding(dp(14), dp(16), dp(14), dp(16));
        scrollApps.setClipChildren(false);
        scrollApps.setClipToPadding(false);
        scrollApps.addView(filaApps, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams appsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        appsLp.topMargin = dp(14);
        columna.addView(scrollApps, appsLp);

        contenido = new ScrollView(this);
        contenido.setVerticalScrollBarEnabled(false);
        contenido.setClipChildren(false);
        contenido.setClipToPadding(false);
        contenido.setPadding(0, dp(10), 0, 0);
        contenido.addView(columna, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.topMargin = dp(190);
        principal.addView(contenido, lp);
    }

    private void construirContenido() {
        filaFavs.removeAllViews();
        filaApps.removeAllViews();

        Set<String> yaMostradas = new HashSet<String>();
        yaMostradas.add(PAQUETE_TV);
        yaMostradas.add(getPackageName());

        filaFavs.addView(tarjeta("TV en directo", tileDe(PAQUETE_TV), new Runnable() {
            @Override
            public void run() { abrirTv(); }
        }));

        for (final String[] f : FAVORITAS) {
            yaMostradas.add(f[1]);
            if (!instalada(f[1])) continue;
            final String pkg = f[1];
            filaFavs.addView(tarjeta(f[0], tileDe(pkg), new Runnable() {
                @Override
                public void run() { abrir(pkg); }
            }));
        }

        // Resto de aplicaciones instaladas
        List<ResolveInfo> lista = new ArrayList<ResolveInfo>();
        String[] categorias = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categorias) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (!yaMostradas.add(p)) continue;
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
            filaApps.addView(tarjeta(ri.loadLabel(pm).toString(), tileDe(ri), new Runnable() {
                @Override
                public void run() { abrir(pkg); }
            }));
        }

        if (getCurrentFocus() == null && filaFavs.getChildCount() > 0) {
            filaFavs.getChildAt(0).requestFocus();
        }
    }

    /** Tarjeta de app: rectángulo redondeado con el banner, se agranda al enfocar. */
    private View tarjeta(final String nombre, Drawable tile, final Runnable accion) {
        final int radio = dp(16);
        final int color = colorDe(tile);

        final FrameLayout caja = new FrameLayout(this);
        caja.setFocusable(true);
        caja.setClickable(true);
        caja.setElevation(dp(4));
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
        iv.setAlpha(0.9f);
        caja.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final GradientDrawable aro = new GradientDrawable();
        aro.setColor(Color.TRANSPARENT);
        aro.setStroke(dp(3), Color.WHITE);
        aro.setCornerRadius(radio);

        caja.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean tiene) {
                float s = tiene ? 1.1f : 1f;
                v.animate().scaleX(s).scaleY(s).translationZ(tiene ? dp(18) : 0f)
                        .setInterpolator(rebote).setDuration(240).start();
                iv.setAlpha(tiene ? 1f : 0.9f);
                caja.setForeground(tiene ? aro : null);
                if (tiene) {
                    titulo.setText(nombre);
                    cambiarGlow(color);
                }
            }
        });
        caja.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { accion.run(); }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(200), dp(112));
        lp.setMargins(dp(6), 0, dp(6), 0);
        caja.setLayoutParams(lp);
        return caja;
    }

    // ------------------------------------------------------------------
    // BRILLO DE COLOR según la app enfocada
    // ------------------------------------------------------------------
    private void aplicarGlow(int c) {
        int rgb = c & 0x00FFFFFF;
        glowDrawable.setColors(new int[]{0xB3000000 | rgb, rgb});
    }

    private void cambiarGlow(int destino) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowActual, destino);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                glowActual = (Integer) a.getAnimatedValue();
                aplicarGlow(glowActual);
            }
        });
        glowAnim.start();
    }

    /** Color dominante (el más saturado) de un banner, oscurecido para usarlo de brillo. */
    private int colorDe(Drawable d) {
        try {
            Drawable.ConstantState cs = d.getConstantState();
            if (cs == null) return COLOR_NEUTRO;
            Drawable copia = cs.newDrawable().mutate();
            int w = 24, hh = 14;
            Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            copia.setBounds(0, 0, w, hh);
            copia.draw(cv);

            float[] hsv = new float[3];
            float r = 0, g = 0, bl = 0, peso = 0;
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < hh; y++) {
                    int px = b.getPixel(x, y);
                    if (Color.alpha(px) < 128) continue;
                    Color.colorToHSV(px, hsv);
                    float wgt = hsv[1] * hsv[2];
                    r += Color.red(px) * wgt;
                    g += Color.green(px) * wgt;
                    bl += Color.blue(px) * wgt;
                    peso += wgt;
                }
            }
            b.recycle();
            if (peso < 1f) return COLOR_NEUTRO;

            int c = Color.rgb(Math.round(r / peso), Math.round(g / peso), Math.round(bl / peso));
            Color.colorToHSV(c, hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.1f);
            hsv[2] = Math.max(0.35f, Math.min(0.6f, hsv[2]));
            return Color.HSVToColor(hsv) | 0xFF000000;
        } catch (Exception e) {
            return COLOR_NEUTRO;
        }
    }

    // ------------------------------------------------------------------
    // SELECTOR HDMI (modal)
    // ------------------------------------------------------------------
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
        principal.setVisibility(View.GONE);

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
            principal.setVisibility(View.VISIBLE);
            if (filaFavs.getChildCount() > 0) filaFavs.getChildAt(0).requestFocus();
        }
    }

    // ------------------------------------------------------------------
    // LANZAR APPS
    // ------------------------------------------------------------------
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

    // ------------------------------------------------------------------
    // FONDOS ROTATIVOS
    // ------------------------------------------------------------------
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

    // ------------------------------------------------------------------
    // TARJETAS / ICONOS
    // ------------------------------------------------------------------
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
        c.drawRoundRect(new RectF(cx - 16, cy - 2, cx + 16, cy + 3), 2, 2, p);
        return new BitmapDrawable(getResources(), b);
    }

    private Drawable dibujarIconoWifi(int color) {
        int s = 64;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setStrokeCap(Paint.Cap.ROUND);

        float cx = s / 2f;
        float cy = 50f;
        for (int i = 1; i <= 3; i++) {
            float r = i * 14f;
            c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 225, 90, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, 4f, p);
        return new BitmapDrawable(getResources(), b);
    }

    private Drawable dibujarIconoEngranaje(int color) {
        int s = 64;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setStrokeCap(Paint.Cap.ROUND);

        float cx = s / 2f;
        float cy = s / 2f;
        c.drawCircle(cx, cy, 13f, p);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            c.drawLine(cx + cos * 18f, cy + sin * 18f, cx + cos * 25f, cy + sin * 25f, p);
        }
        return new BitmapDrawable(getResources(), b);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
