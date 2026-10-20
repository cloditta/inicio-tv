package es.marta.inicio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Genera paisajes de montanas por capas (cielo, sol o luna, crestas y niebla).
 * Se usa como fondo cuando no hay fotos en la carpeta de fondos.
 */
final class Paisaje {

    static final int ESTILOS = 5;

    /** Por estilo: cielo arriba, cielo en el horizonte, sol o luna, niebla. */
    private static final int[][] CIELOS = {
            {0xFF1B2B4B, 0xFFF6B77A, 0xFFFFE3B0, 0xFFF8C9A0},   // amanecer
            {0xFF14303A, 0xFF8FB8A5, 0xFFEAF2D0, 0xFFBCD6C4},   // bosque
            {0xFF0B2545, 0xFF8ED1E8, 0xFFFFFFFF, 0xFFBFE3EE},   // lago
            {0xFF2B1A4D, 0xFFFF8E5E, 0xFFFFC27A, 0xFFFFA77F},   // atardecer
            {0xFF04060E, 0xFF1D2D52, 0xFFDDE6FF, 0xFF2B3E6B},   // noche
    };

    /** Colores de las 5 crestas, de la mas lejana a la mas cercana. */
    private static final int[][] CRESTAS = {
            {0xFF9A7FA0, 0xFF7A6590, 0xFF554A78, 0xFF38355A, 0xFF1D1F36},
            {0xFF8FB39C, 0xFF63917A, 0xFF3F6C59, 0xFF244A3E, 0xFF0F2A24},
            {0xFF8CB4D6, 0xFF5E8FBA, 0xFF3C6A97, 0xFF264A73, 0xFF122B4A},
            {0xFFB06A92, 0xFF8A4F86, 0xFF633A78, 0xFF402A62, 0xFF1F1840},
            {0xFF2C3E6B, 0xFF222F55, 0xFF19244A, 0xFF111A38, 0xFF0A1024},
    };

    private Paisaje() {
    }

    static Bitmap crear(int w, int h, int estilo, long semilla) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random r = new Random(semilla);
        int[] cielo = CIELOS[estilo];
        int[] crestas = CRESTAS[estilo];

        // Cielo
        p.setShader(new LinearGradient(0, 0, 0, h * 0.78f, cielo[0], cielo[1],
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // Resplandor y disco del sol (o la luna)
        float sx = w * (0.25f + 0.5f * r.nextFloat());
        float sy = h * (0.30f + 0.12f * r.nextFloat());
        int sol = cielo[2] & 0x00FFFFFF;
        p.setShader(new RadialGradient(sx, sy, h * 0.55f,
                new int[]{sol | 0xCC000000, sol | 0x33000000, sol},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(cielo[2]);
        c.drawCircle(sx, sy, h * 0.05f, p);

        // Estrellas (solo en el estilo nocturno)
        if (estilo == 4) {
            p.setColor(0xFFFFFFFF);
            for (int i = 0; i < 160; i++) {
                float x = r.nextFloat() * w;
                float y = r.nextFloat() * h * 0.55f;
                p.setAlpha(60 + r.nextInt(180));
                c.drawCircle(x, y, 0.8f + r.nextFloat() * 1.6f, p);
            }
            p.setAlpha(255);
        }

        // Crestas con niebla entre capas
        int nb = cielo[3] & 0x00FFFFFF;
        int n = crestas.length;
        for (int i = 0; i < n; i++) {
            float base = h * (0.52f + 0.085f * i);
            float amp = h * (0.24f - 0.03f * i);
            float f1 = (float) (2 * Math.PI * (1.0 + r.nextFloat() * 1.2) / w);
            float f2 = (float) (2 * Math.PI * (3.0 + r.nextFloat() * 2.5) / w);
            float f3 = (float) (2 * Math.PI * (8.0 + r.nextFloat() * 5.0) / w);
            float p1 = r.nextFloat() * 6.28f;
            float p2 = r.nextFloat() * 6.28f;
            float p3 = r.nextFloat() * 6.28f;

            Path path = new Path();
            path.moveTo(0, h);
            for (int x = 0; x <= w; x += 6) {
                double v = 0.60 * (1 - Math.abs(Math.sin(f1 * x + p1)))
                        + 0.30 * (1 - Math.abs(Math.sin(f2 * x + p2)))
                        + 0.10 * (0.5 + 0.5 * Math.sin(f3 * x + p3));
                path.lineTo(x, (float) (base - amp * v));
            }
            path.lineTo(w, h);
            path.close();

            int arriba = mezclar(crestas[i], cielo[3], 0.25f);
            p.setShader(new LinearGradient(0, base - amp, 0, h, arriba, crestas[i],
                    Shader.TileMode.CLAMP));
            c.drawPath(path, p);

            if (i < n - 1) {
                float y0 = base - amp * 0.3f;
                p.setShader(new LinearGradient(0, y0, 0, base + h * 0.12f,
                        nb, nb | 0x59000000, Shader.TileMode.CLAMP));
                c.drawRect(0, y0, w, h, p);
            }
        }

        // Vineta suave en los bordes
        p.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x00000000, 0x66000000},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        return b;
    }

    private static int mezclar(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
