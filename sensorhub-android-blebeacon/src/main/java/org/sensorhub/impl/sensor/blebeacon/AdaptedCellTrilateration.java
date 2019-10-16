package org.sensorhub.impl.sensor.blebeacon;

public class AdaptedCellTrilateration {
    static double[] trackPhone(double[][] locations, double[] distances){
        double[] computedLocation = new double[2];

        double A = 2 * locations[1][0] - 2 * locations[0][0];
        double B = 2 * locations[1][1] - 2 * locations[0][1];
        double C = Math.pow(distances[0], 2) - Math.pow(distances[1], 2) - Math.pow(locations[0][0], 2) + Math.pow(locations[1][0], 2) - Math.pow(locations[0][1], 2) + Math.pow(locations[1][1], 2);

        double D = 2 * locations[2][0] - 2 * locations[1][0];
        double E = 2 * locations[2][1] - 2 * locations[1][1];
        double F = Math.pow(distances[1], 2) - Math.pow(distances[2], 2) - Math.pow(locations[1][0], 2) + Math.pow(locations[2][0], 2) - Math.pow(locations[1][1], 2) + Math.pow(locations[2][1], 2);

        double xCoord = (C*E - F*B) / (E*A - B*D);
        double yCoord = (C*D - A*F) / (B*D - A*E);

        computedLocation[0] = xCoord;
        computedLocation[1] = yCoord;

        return computedLocation;
    }
}
