package com.z_iti_271304_u2_e04;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.List;

public class OverlayView extends View {

    private List<BoundingBox> boundingBoxes;
    private Paint boxPaint;
    private Paint textPaint;

    //Constructor
    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    //Constructor
    public OverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        /*
        Método para inicializar la vista
        Crea un paint y define los colores
        */
        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5.0f);

        textPaint = new Paint();
        textPaint.setTextSize(25);
        textPaint.setColor(Color.WHITE);
    }

    public void invalidateDraw() {
        invalidate();
    }

    public void setBoundingBoxes(List<BoundingBox> boundingBoxes) {
        /*
        Método SET para obtener las detecciones (Bounding boxes)
        */
        this.boundingBoxes = boundingBoxes;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        /*
        Método para dibujar las cajas de las detecciones
        */
        super.onDraw(canvas);

        //Si la lista de detecciones no está vacía (no es nula)
        if (boundingBoxes != null) {
            for (BoundingBox box : boundingBoxes) {
                float left = box.getX1() * getWidth();
                float top = box.getY1() * getHeight();
                float right = box.getX2() * getWidth();
                float bottom = box.getY2() * getHeight();

                //Nombre de la moneda
                String drawableText = box.getClsName();

                //En caso de ser aguila de 1, 2 o 5
                if(drawableText.equals("$125"))
                    drawableText = "Águila";
                else if(drawableText.equals("$0"))
                    drawableText = "Otra";

                // Centrar el texto horizontalmente respecto a la caja
                float textWidth = boxPaint.measureText(drawableText);
                float textX = left + (right - left) / 2 - textWidth / 2;
                float textY = top - 10; // Coloca el texto justo encima de la caja

                // Dibuja la caja
                canvas.drawRect(left, top, right, bottom, boxPaint);

                // Dibuja el nombre de la clase encima de la caja
                canvas.drawText(drawableText, textX, textY, textPaint);
            }
        }
    }
}
