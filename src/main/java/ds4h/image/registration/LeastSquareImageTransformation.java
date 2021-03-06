package ds4h.image.registration;

import ds4h.dialog.align.setting.SettingEvent;
import ds4h.image.model.manager.slide.SlideImage;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import mpicbg.ij.Mapping;
import mpicbg.ij.TransformMeshMapping;
import mpicbg.models.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LeastSquareImageTransformation {
    public static final int MINIMUM_ROI_NUMBER = 3;

    private LeastSquareImageTransformation() {
    }

    /**
     * Performs the least square transformation between two BufferedImages with a series of fixed parameters.
     */
    public static ImagePlus transform(SlideImage sourceOriginal, SlideImage source, SlideImage templateOriginal, SlideImage template, SettingEvent event) {
        Mapping<?> mapping;
        final MovingLeastSquaresTransform t = new MovingLeastSquaresTransform();
        try {
            Class<? extends Model<?>> modelClass = LeastSquareImageTransformation.getTransformationModel(event);
            t.setModel(modelClass);
        } catch (Exception e) {
            IJ.showMessage(e.getMessage());
        }
        t.setAlpha(2.0f);
        int meshResolution = 64;
        final ImagePlus target = templateOriginal.createImagePlus();
        final ImageProcessor ipSource = sourceOriginal.getProcessor();
        final ImageProcessor ipTarget = sourceOriginal.getProcessor().createProcessor(templateOriginal.getWidth(), templateOriginal.getHeight());
        final List<Point> sourcePoints = LeastSquareImageTransformation.getPoints(source);
        final List<Point> templatePoints = LeastSquareImageTransformation.getPoints(template);
        final int numMatches = Math.min(sourcePoints.size(), templatePoints.size());
        final ArrayList<PointMatch> matches = new ArrayList<>();

        for (int i = 0; i < numMatches; ++i)
            matches.add(new PointMatch(sourcePoints.get(i), templatePoints.get(i)));
        try {
            t.setMatches(matches);
            mapping = new TransformMeshMapping<>(new CoordinateTransformMesh(t, meshResolution, sourceOriginal.getWidth(), sourceOriginal.getHeight()));
        } catch (final Exception e) {
            IJ.showMessage(e.getMessage());
            IJ.showMessage("Not enough landmarks selected to find a transformation model.");
            return null;
        }
        ipSource.setInterpolationMethod(ImageProcessor.BILINEAR);
        mapping.mapInterpolated(ipSource, ipTarget);
        target.setProcessor("Transformed" + sourceOriginal.getTitle(), ipTarget);
        return target;
    }

    private static Class<? extends Model<?>> getTransformationModel(SettingEvent event) {
        Class<? extends Model<?>> model = TranslationModel2D.class;
        if (event.isAffine()) {
            model = AffineModel2D.class;
        }
        if (event.isProjective()) {
            // THIS ONE NEEDS FOUR ROIS
            model = HomographyModel2D.class;
        }
        return model;
    }

    private static List<Point> getPoints(SlideImage slideImage) {
        return Arrays.stream(slideImage.getManager().getRoisAsArray()).map(roi -> {
            double oldX = roi.getRotationCenter().xpoints[0];
            double oldY = roi.getRotationCenter().ypoints[0];
            return new Point(new double[]{oldX, oldY});
        }).collect(Collectors.toList());
    }
}
