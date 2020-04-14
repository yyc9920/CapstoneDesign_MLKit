package com.skj.firebasefacedetection_mycode.Interfaces;

import android.graphics.Bitmap;

import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.skj.firebasefacedetection_mycode.Process_part.FrameMetadata;
import com.skj.firebasefacedetection_mycode.Process_part.GraphicOverlay;

/** An inferface to process the images with different ML Kit detectors and custom image models. */
public interface FrameReturn {
    void onFrame(Bitmap image, FirebaseVisionFace face, FrameMetadata frameMetadata, GraphicOverlay graphicOverlay);
}
