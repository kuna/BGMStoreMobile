package com.kuna.bgmstoremobile;

import java.net.URLEncoder;

public class BGMStoreQuery {
	public static int limit_count = 0;
	public static String q = "";
	public static String q_type = "title";
	public static String sort_by = "document_num";
	public static String sort_type = "desc";
	public static int member_num = -1;
	public static String q_mode = "general";
	
	public static String getQuery() {
		String r = "limit_count=" + Integer.toString(limit_count)
				+ "&q=" + q
				+ "&q_type=" + q_type
				+ "&sort_by=" + sort_by
				+ "&sort_type=" + sort_type
				+ "&member_num=" + ((member_num>=0)?Integer.toString(member_num):"")
				+ "&q_mode=" + q_mode;
		return r;//URLEncoder.encode(r);
	}
}
