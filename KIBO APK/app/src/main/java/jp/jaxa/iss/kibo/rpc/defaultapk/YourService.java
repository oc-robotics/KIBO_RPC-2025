package jp.jaxa.iss.kibo.rpc.defaultapk;

import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;

import org.opencv.core.Mat;

/**
 * Class meant to handle commands from the Ground Data System and execute them
 * in Astrobee.
 */

public class YourService extends KiboRpcService {
    @Override
    protected void runPlan1() {
        // The mission starts.
        api.startMission();

        // Move to a point.
//        Point point = new Point(11.48d, -10.58d, 5.57d);
        Point[] points = {
                new Point(10.42d, -10.58d, 4.82d),
                new Point(10.3d, -9.25d, 3.76203d),
                new Point(10.3d, -8.4d, 3.76093d),
                new Point(9.866984d, -7.34d, 4.32d)
        };

        Quaternion[] quaternions = {
                new Quaternion(0f, 0f, -0.707f, 0.707f),
                new Quaternion(0.2f, 0.2f, 0.2f, 0.94f),
                new Quaternion(0f, 0.707f, 0f, 0.707f),
                new Quaternion(-0.5f, 0.5f, -0.5f, 0.5f)
        };

        for (int i = 0; i < points.length; i++) {
            api.moveTo(points[i], quaternions[i], false);
        }


        // Get a camera image.
        Mat image = api.getMatNavCam();

        /*
         * *****************************************************************************
         * ***
         */
        /*
         * Write your code to recognize the type and number of landmark items in each
         * area!
         */
        /* If there is a treasure item, remember it. */
        /*
         * *****************************************************************************
         * ***
         */

        // When you recognize landmark items, let’s set the type and number.
        api.setAreaInfo(1, "item_name", 1);

        /* **************************************************** */
        /* Let's move to each area and recognize the items. */
        /* **************************************************** */

        // When you move to the front of the astronaut, report the rounding completion.
        Point point = new Point(11.143d, -6.7607d, 4.9654d);
        Quaternion quaternion = new Quaternion(0f, 0f, 0.707f, 0.707f);
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
}
