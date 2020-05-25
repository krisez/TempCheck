package temp.check.app;

import java.math.BigInteger;

public class ByteUtils {
	/**
	 * 私有构造函数，不允许该类被初始化
	 */
	private ByteUtils()
	{
	}

	/**
	 * 把字节数组转换成16进制字符
	 *
	 * @param bArray byte数组
	 * @return 16进制字符
	 */
	public static String bytesToHexString(byte[] bArray)
	{
		StringBuffer sb = new StringBuffer(bArray.length);
		String sTemp;
		for (int i = 0; i < bArray.length; i++)
		{
			sTemp = Integer.toHexString(0xFF & bArray[i]);
			if (sTemp.length() < 2)
			{
				sb.append(0);
			}
			sb.append(sTemp.toUpperCase());
		}
		return sb.toString();
	}

	/**
	 * 把字节数组转换成16进制字符
	 *
	 * @param arg byte数组
	 * @param length 长度
	 * @return 16进制字符
	 */
	public static String bytesToHexString2(byte[] arg,int length)
	{
		StringBuilder result = new StringBuilder();
		if (arg != null) {
			for (int i = 0; i < length; i++) {
				result.append(Integer.toHexString(arg[i] < 0 ? arg[i] + 256 : arg[i]).length() == 1 ? "0" + Integer.toHexString(arg[i] < 0 ? arg[i] + 256 : arg[i])
						: Integer.toHexString(arg[i] < 0 ? arg[i] + 256 : arg[i])).append(" ");
			}
			return result.toString();
		}
		return "";
	}

	/**
	 * 将16进制字符串转化为byte数组
	 *
	 * @param hex  16进制字符串
	 * @return 字节数组
	 */
	public static byte[] hexStringToBytes(String hex)
	{
		int length = hex.length() / 2;
		hex = hex.toUpperCase();
		char[] hexChars = hex.toCharArray();
		byte[] d = new byte[length];
		for (int i = 0; i < length; i++)
		{
			int pos = i * 2;
			d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
		}
		return d;
	}

	/**
	 * 将一个字符转化为一个字节
	 * @param c 字符
	 * @return 字节
	 */
	private static byte charToByte(char c)
	{
		return (byte) "0123456789ABCDEF".indexOf(c);
	}

	/**
	 * 16进制字符串转10进制整数
	 *
	 * @param hexStr 16进制字符串
	 * @return 10进制整数
	 */
	public static int hexStrToInt(String hexStr) {
		return new BigInteger(hexStr, 16).intValue();
	}
}
