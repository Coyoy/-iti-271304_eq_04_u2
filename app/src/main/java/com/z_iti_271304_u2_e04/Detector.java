package com.z_iti_271304_u2_e04;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


public class Detector {

    private Context context;
    private String modelPath;
    private String labelPath;
    private DetectorListener detectorListener;
    private Interpreter interpreter;
    private List<String> labels = new ArrayList<>();
    private int tensorWidth;
    private int tensorHeight;
    private int numChannel;
    private int numElements;

    //Acerca del modelo
    private static final float INPUT_MEAN = 0f;
    private static final float INPUT_STANDARD_DEVIATION = 255f;
    private static final DataType INPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final DataType OUTPUT_IMAGE_TYPE = DataType.FLOAT32;
    private float CONFIDENCE_THRESHOLD = 0.5F; //Confianza predefinida para la detección
    private static final float IOU_THRESHOLD = 0.5F;

    //Procesador de la imagen
    private ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
            .add(new CastOp(INPUT_IMAGE_TYPE))
            .build();

    //Constuctor de la clse
    public Detector(Context context, String modelPath, String labelPath, DetectorListener detectorListener) {
        this.context = context;
        this.modelPath = modelPath;
        this.labelPath = labelPath;
        this.detectorListener = detectorListener;
    }

    public void setConfidenceThreshold(float CONFIDENCE_THRESHOLD) {
        /*
        Actualiza la confianza del modelo
         */
        this.CONFIDENCE_THRESHOLD = CONFIDENCE_THRESHOLD;
    }

    public void setup() {
        /*
        Inicializa el interprete de imagenes
        */
        try {
            interpreter = new Interpreter(FileUtil.loadMappedFile(context, modelPath), new Interpreter.Options());
            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();

            tensorWidth = inputShape[1];
            tensorHeight = inputShape[2];
            numChannel = outputShape[1];
            numElements = outputShape[2];

            InputStream inputStream = context.getAssets().open(labelPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty())
                labels.add(line);
            reader.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clear() {
        /*
        Detiene y libera el interprete
        */
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }

    public void detect(Bitmap frame) {
        /*
        Método que e encarga de realizar las detecciones
        */
        if (interpreter == null || tensorWidth == 0 || tensorHeight == 0 || numChannel == 0 || numElements == 0) return;

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false);
        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(resizedBitmap);
        TensorImage processedImage = imageProcessor.process(tensorImage);
        TensorBuffer output = TensorBuffer.createFixedSize(new int[]{1, numChannel, numElements}, OUTPUT_IMAGE_TYPE);
        interpreter.run(processedImage.getBuffer(), output.getBuffer());

        //Lista de cajas
        List<BoundingBox> bestBoxes = bestBox(output.getFloatArray());

        if (bestBoxes == null)
            detectorListener.onEmptyDetect();
        else
            detectorListener.onDetect(bestBoxes);
    }

    private List<BoundingBox> bestBox(float[] array) {
        /*
        Método para obtener las mejores detecciones (las coordenadas)
        Válida que no haya más de dos  detecciones muy cerca una de otra
        */
        List<BoundingBox> boundingBoxes = new ArrayList<>();
        for (int c = 0; c < numElements; c++) {
            float maxConf = -1.0f;
            int maxIdx = -1;

            for (int j = 4, arrayIdx = c + numElements * j; j < numChannel; j++, arrayIdx += numElements) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx];
                    maxIdx = j - 4;
                }
            }

            //Agrega la detección (La caja) si la confianza del interprete es mayor a la confianza definida por el usuario
            if (maxConf > CONFIDENCE_THRESHOLD) {
                String clsName = labels.get(maxIdx);
                float cx = array[c];
                float cy = array[c + numElements];
                float w = array[c + numElements * 2];
                float h = array[c + numElements * 3];
                float x1 = cx - (w / 2F);
                float y1 = cy - (h / 2F);
                float x2 = cx + (w / 2F);
                float y2 = cy + (h / 2F);
                if (x1 >= 0F && x1 <= 1F && y1 >= 0F && y1 <= 1F && x2 >= 0F && x2 <= 1F && y2 >= 0F && y2 <= 1F) {
                    boundingBoxes.add(new BoundingBox(x1, y1, x2, y2, cx, cy, w, h, maxConf, maxIdx, clsName));
                }
            }
        }

        return boundingBoxes.isEmpty() ? null : applyNMS(boundingBoxes);
    }

    private List<BoundingBox> applyNMS(List<BoundingBox> boxes) {
        /*
        Válida que no hayan dos detecciones muy cerca
        Si se intersectan las detecciones, elimina una de ellas
        */
        boxes.sort((b1, b2) -> Float.compare(b2.getCnf(), b1.getCnf()));
        List<BoundingBox> selectedBoxes = new ArrayList<>();

        while (!boxes.isEmpty()) {
            BoundingBox first = boxes.remove(0);
            selectedBoxes.add(first);
            boxes.removeIf(nextBox -> calculateIoU(first, nextBox) >= IOU_THRESHOLD);
        }

        return selectedBoxes;
    }

    private float calculateIoU(BoundingBox box1, BoundingBox box2) {
        /*
        Método que calcula los datos de las cajas de detección
        Obtiene el área de intersección
        */
        float x1 = Math.max(box1.getX1(), box2.getX1());
        float y1 = Math.max(box1.getY1(), box2.getY1());
        float x2 = Math.min(box1.getX2(), box2.getX2());
        float y2 = Math.min(box1.getY2(), box2.getY2());

        float intersectionArea = Math.max(0F, x2 - x1) * Math.max(0F, y2 - y1);
        float box1Area = box1.getW() * box1.getH();
        float box2Area = box2.getW() * box2.getH();

        return intersectionArea / (box1Area + box2Area - intersectionArea);
    }

    public interface DetectorListener {
        /*
        Listener para detectar las monedas
        Interfaz para obtener las detecciones desde el Main
        */
        void onEmptyDetect();
        void onDetect(List<BoundingBox> boundingBoxes);
    }
}
