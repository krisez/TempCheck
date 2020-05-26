package temp.check.app;

public class FrameUtil {
    public static String getStandardFrame(String order) {
        return "66cc" +
                getSum(order) +
                order +
                getCheckSum(order);
    }

    public static String getSum(String data) {
        StringBuilder builder = new StringBuilder();
        int sum = data.length() / 2 + 1;
        if (sum <= 0x000f) {
            builder.append("000").append(Integer.toHexString(sum));
        } else if (sum <= 0x00ff) {
            builder.append("00").append(Integer.toHexString(sum));
        } else if (sum <= 0x0fff) {
            builder.append("0").append(Integer.toHexString(sum));
        } else if (sum <= 0xffff) {
            builder.append(Integer.toHexString(sum));
        }
        return builder.toString();
    }

    public static String getCheckSum(String data) {
        StringBuilder builder = new StringBuilder();
        int[] hexData = new int[data.length() / 2];
        int sum = 0;
        for (int i = 0; i < data.length(); i += 2) {
            hexData[i / 2] = Integer.parseInt(data.substring(i, i + 2), 16);
            sum += hexData[i / 2];
        }
        sum += Integer.parseInt(getSum(data), 16);
        String resultStr = Integer.toHexString(sum);
        builder.append(resultStr.substring(resultStr.length() - 2));
        return builder.toString();
    }

    public static String getCheckSum(String data, String sumString) {
        StringBuilder builder = new StringBuilder();
        int[] hexData = new int[data.length() / 2];
        int sum = 0;
        for (int i = 0; i < data.length(); i += 2) {
            hexData[i / 2] = Integer.parseInt(data.substring(i, i + 2), 16);
            sum += hexData[i / 2];
        }
        sum += Integer.parseInt(sumString,16);
        String resultStr = Integer.toHexString(sum);
        builder.append(resultStr.substring(resultStr.length() - 2));
        return builder.toString();
    }


    public static String getDataFrame(String id, String data) {
        StringBuilder builder = new StringBuilder();
        builder.append("300103").append(id).append("08");
        data = Integer.toBinaryString(Integer.parseInt(data));
        if (data.length() < 16) {
            for (int i = 0; i < 16 - data.length(); i++) {
                builder.append(0);
            }
        }
        builder.append(data);
        return getStandardFrame(builder.toString());
    }

    public static String getTargetTData(String targetT) {
        targetT = Integer.toHexString(Integer.parseInt(targetT));

        StringBuilder builder = new StringBuilder();

        builder.append(targetT);
        int length = builder.length();
        for (int i = 0; i < 16 - length; i++) {

            if (i == 7){
                builder.append(01);
            }else {
                builder.append(0);
            }
        }
        //System.out.println(builder.length());
        return builder.toString();
    }

    public static String getStartData(int status){
        String data = Integer.toHexString(status);
        if (data.length() == 1){
            data = "0" + data;
        }
        StringBuilder builder = new StringBuilder();


        System.out.println("data = " + data);
        for (int i = 0; i < 15; i++) {
            if (i == 8){
                builder.append(data);
            }else {
                builder.append(0);
            }

        }
        return builder.toString();
    }

}
