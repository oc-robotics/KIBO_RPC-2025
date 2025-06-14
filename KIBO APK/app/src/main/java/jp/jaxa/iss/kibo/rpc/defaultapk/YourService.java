package jp.jaxa.iss.kibo.rpc.defaultapk;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import gov.nasa.arc.astrobee.Kinematics;
import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;

import org.opencv.android.Utils;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Class meant to handle commands from the Ground Data System and execute them
 * in Astrobee.
 */

public class YourService extends KiboRpcService {

    private final String[] TEMPLATE_FILE_NAME = {
            "coin.png",
            "compass.png",
            "coral.png",
            "crystal.png",
            "diamond.png",
            "emerald.png",
            "fossil.png",
            "key.png",
            "letter.png",
            "shell.png",
            "treasure_box.png"
    };
    private final String[] TEMPLATE_NAME = {
            "coin",
            "compass",
            "coral",
            "crystal",
            "diamond",
            "emerald",
            "fossil",
            "key",
            "letter",
            "shell",
            "treasure_box"
    };

    @Override
    protected void runPlan1() {
        // The mission starts.
        api.startMission();

        // Move to a point.
        Point point = new Point(10.9d, -9.92284d, 5.195d);
        Quaternion quaternion = new Quaternion(0f, 0f, -0.707f, 0.707f);
        api.moveTo(point, quaternion, false);

        /* *************************************************** */
        /* OpenCV image processes from tutorial */
        /* *************************************************** */

        // Get a camera image.
        Mat image = api.getMatNavCam();
        api.saveMatImage(image, "Area1.png");

        // Detect AR Tag
        Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
        List<Mat> corners = new ArrayList<>();
        Mat ids = new Mat();
        DetectorParameters parameters = DetectorParameters.create();
        Aruco.detectMarkers(image, dictionary, corners, ids, parameters);

        // Get camera matrix
        Mat cameraMatrix = new Mat(3, 3, CvType.CV_64F);
        cameraMatrix.put(0, 0, api.getNavCamIntrinsics()[0]);

        // Get Lens distortion parameters
        Mat cameraCoefficients = new Mat(1, 5, CvType.CV_64F);
        cameraCoefficients.put(0, 0, api.getNavCamIntrinsics()[1]);
        cameraCoefficients.convertTo(cameraCoefficients, CvType.CV_64F);

        // Get Pose Estimation
        float markerLength = 0.05f; //meters
        Mat rotationVectors = new Mat();
        Mat translationVectors = new Mat();
        Aruco.estimatePoseSingleMarkers(corners, markerLength, cameraMatrix, cameraCoefficients, rotationVectors, translationVectors);

        // Convert rotation and translation vectors to Point and Quaternion
        if (ids.rows() > 0){
            // rotation
            double[] rotationVectorArray = new double[3];
            rotationVectors.row(0).get(0, 0, rotationVectorArray);
            Mat rotation = new Mat(3, 1, CvType.CV_64F);
            rotation.put(0, 0, rotationVectorArray);
            Mat rotationMatrix = new Mat();
            Calib3d.Rodrigues(rotation, rotationMatrix);
            quaternion = rotationMatrixToAstrobeeQuaternion(rotationMatrix);

            // translation
            double[] translationVectorArray = new double[3];
            translationVectors.row(0).get(0, 0, translationVectorArray);
            point = new Point((float) translationVectorArray[0], (float) translationVectorArray[1], (float) translationVectorArray[2]);

            /*
            * Getting Robot's current Position
            * TranslationVectors Coordiante System:
            * Origin: Located at camera's optical center
            * X-axis: Points to the right of camera
            * Y-axis: Points down of camera
            * Z-axis: Points forward (away from the camera)
            */
            Kinematics currentKinematrics = api.getRobotKinematics();
            point = new Point(
                    (float) (currentKinematrics.getPosition().getX() - translationVectorArray[0]),
                    (float) (currentKinematrics.getPosition().getY() - translationVectorArray[2]),
                    (float) (currentKinematrics.getPosition().getZ() - translationVectorArray[1])
            );
            float theta = (float) (-Math.PI / 2.0);
            Quaternion offsetQuat = new Quaternion(
                    (float) Math.sin(theta / 2.0),
                    0.0f,
                    0.0f,
                    (float) Math.cos(theta / 2.0)
            );
            quaternion = multiplyQuaternions(offsetQuat, quaternion);
            api.moveTo(point, quaternion, true);
        }

        image = api.getMatNavCam();
        api.saveMatImage(image, "Area1Closer.png");

        // Undistorted image
        Mat undistortImg = new Mat();
        Calib3d.undistort(image, undistortImg, cameraMatrix, cameraCoefficients);
        api.saveMatImage(undistortImg, "Area1UndistortImage.png");

        // Pattern Matching
        // Load template images
        Mat[] templates = new Mat[TEMPLATE_FILE_NAME.length];
        for (int i = 0; i < TEMPLATE_FILE_NAME.length; i++) {
            try{
                InputStream inputStream = getAssets().open(TEMPLATE_FILE_NAME[i]);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                Mat mat = new Mat();
                Utils.bitmapToMat(bitmap, mat);

                // convert to grayscale
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);

                // Assign to an array of templates
                templates[i] = mat;

                inputStream.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Number of matches for each template
        int templateMatchCnt[] = new int[templates.length];

        // Get the number of template matches
        for (int tempNum = 0; tempNum < templates.length; tempNum++){
            // Number of matches
            List<org.opencv.core.Point> matches = new ArrayList<>();

            // Loading template image and target Image
            Mat template = templates[tempNum].clone();
            Mat targetImg = undistortImg.clone();

            int widthMin = 20;
            int widthMax = 100;
            int changeWidth = 5;
            int changeAngle = 45;

            for (int size = widthMin; size <= widthMax; size += changeWidth){
                for (int angle = 0; angle <= 360; angle += changeAngle){
                    Mat resizedTemp = resizeImg(template, size);
                    Mat rotateResizedTemp = rotateImg(resizedTemp, angle);

                    // result outputs value from 0-1
                    // 0: bad match
                    // 1: perfect match
                    Mat result = new Mat();
                    Imgproc.matchTemplate(targetImg, rotateResizedTemp, result, Imgproc.TM_CCOEFF_NORMED);

                    // Get coordinates with similarity greater than or equal to the threshold
                    double threshold = 0.8;
                    Core.MinMaxLocResult mmlr = Core.minMaxLoc(result);
                    double maxVal = mmlr.maxVal;
                    if (maxVal >= threshold){
                        // Extract only results greater than or euqal to the threshold
                        Mat thresholdedResult = new Mat();
                        Imgproc.threshold(result, thresholdedResult, threshold, 1.0, Imgproc.THRESH_TOZERO);

                        // Get coordinates of the matched location
                        for (int y = 0; y < thresholdedResult.rows(); y++){
                            for (int x = 0; x < thresholdedResult.cols(); x++){
                                if (thresholdedResult.get(y, x)[0] > 0){
                                    matches.add(new org.opencv.core.Point(x, y));
                                }
                            }
                        }
                    }
                }
            }

            // Avoid detecting the same location multiple times
            List<org.opencv.core.Point> filteredMatches = removeDuplicates(matches);

            // Number of matches for each template
            templateMatchCnt[tempNum] = filteredMatches.size();
        }

        int mostMatchTemplateNum = getMaxIndex(templateMatchCnt);
        api.setAreaInfo(1, TEMPLATE_NAME[mostMatchTemplateNum], templateMatchCnt[mostMatchTemplateNum]);

        /* **************************************************** */
        /* Let's move to each area and recognize the items. */
        /* **************************************************** */

        // When you move to the front of the astronaut, report the rounding completion.
        point = new Point(11.143d, -6.7607d, 4.9654d);
        quaternion = new Quaternion(0f, 0f, 0.707f, 0.707f);
        api.moveTo(point, quaternion, false);
        api.reportRoundingCompletion();

        /* ********************************************************** */
        /* Write your code to recognize which target item the astronaut has. */
        /* ********************************************************** */

        // Let's notify the astronaut when you recognize it.
        api.notifyRecognitionItem();

        /*
         * *****************************************************************************
         * **************************
         */
        /*
         * Write your code to move Astrobee to the location of the target item (what the
         * astronaut is looking for)
         */
        /*
         * *****************************************************************************
         * **************************
         */

        // Take a snapshot of the target item.
        api.takeTargetItemSnapshot();
    }

    @Override
    protected void runPlan2() {
        // write your plan 2 here.
    }

    @Override
    protected void runPlan3() {
        // write your plan 3 here.
    }

    // You can add your method.
    private String yourMethod() {
        return "your method";
    }

    // Resizing the image to match the size of the template
    private Mat resizeImg(Mat img, int width){
        int height = (int) (img.rows() * ((double) width / img.cols()));
        Mat resizedImg = new Mat();
        Imgproc.resize(img, resizedImg, new Size(width, height));

        return resizedImg;
    }

    // Rotating the image to trial-and-error through pattern matching process
    private Mat rotateImg(Mat img, int angle){
        org.opencv.core.Point center = new org.opencv.core.Point(img.cols() / 2.0, img.rows() / 2.0);
        Mat rotatedMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat rotImg = new Mat();
        Imgproc.warpAffine(img, rotImg, rotatedMat, img.size());

        return rotImg;
    }

    // remove the duplicate since pattern matching could double count
    private List<org.opencv.core.Point> removeDuplicates(List<org.opencv.core.Point> points){
        double length = 10; // within 10 px
        List<org.opencv.core.Point> filteredList = new ArrayList<>();

        for (org.opencv.core.Point point: points){
            boolean isInclude = false;
            for (org.opencv.core.Point checkPoint: filteredList){
                double distance = calculateDistance(point, checkPoint);

                if (distance <= length){
                    isInclude = true;
                    break;
                }
            }

            if (!isInclude){
                filteredList.add(point);
            }
        }

        return filteredList;
    }

    // finding the distance between 2 points (to remove the duplicates)
    private double calculateDistance(org.opencv.core.Point p1, org.opencv.core.Point p2){
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;

        return Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
    }

    // Get the maximum value of an array
    private int getMaxIndex(int[] array){
        int max = 0;
        int maxIndex = 0;

        for (int i = 0; i < array.length; i++){
            if (array[i] > max){
                max = array[i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }

    // converting rotational matrix to Quaternion for Astrobee api
    private static Quaternion rotationMatrixToAstrobeeQuaternion(Mat R) {
        double m00 = R.get(0, 0)[0];
        double m01 = R.get(0, 1)[0];
        double m02 = R.get(0, 2)[0];
        double m10 = R.get(1, 0)[0];
        double m11 = R.get(1, 1)[0];
        double m12 = R.get(1, 2)[0];
        double m20 = R.get(2, 0)[0];
        double m21 = R.get(2, 1)[0];
        double m22 = R.get(2, 2)[0];

        double trace = m00 + m11 + m22;
        double w, x, y, z;
        if (trace > 0) {
            double s = Math.sqrt(trace + 1.0) * 2.0; // s = 4 * w
            w = 0.25 * s;
            x = (m21 - m12) / s;
            y = (m02 - m20) / s;
            z = (m10 - m01) / s;
        } else if ((m00 > m11) && (m00 > m22)) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0; // s = 4 * x
            w = (m21 - m12) / s;
            x = 0.25 * s;
            y = (m01 + m10) / s;
            z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0; // s = 4 * y
            w = (m02 - m20) / s;
            x = (m01 + m10) / s;
            y = 0.25 * s;
            z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0; // s = 4 * z
            w = (m10 - m01) / s;
            x = (m02 + m20) / s;
            y = (m12 + m21) / s;
            z = 0.25 * s;
        }
        // The Astrobee Quaternion constructor takes arguments as (x, y, z, w)
        return new Quaternion((float) x, (float) y, (float) z, (float) w);
    }

    private static Quaternion multiplyQuaternions(Quaternion q1, Quaternion q2) {
        float w1 = q1.getW();
        float x1 = q1.getX();
        float y1 = q1.getY();
        float z1 = q1.getZ();

        float w2 = q2.getW();
        float x2 = q2.getX();
        float y2 = q2.getY();
        float z2 = q2.getZ();

        // Quaternion multiplication formula
        float w = w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2;
        float x = w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2;
        float y = w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2;
        float z = w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2;

        return new Quaternion(x, y, z, w);
    }
}
