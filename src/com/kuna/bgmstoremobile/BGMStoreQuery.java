package com.kuna.bgmstoremobile;

public class BGMStoreQuery {
    public static int sLimitCount = 0;
    public static String sKeyword = "";
    public static String sKeywordType = "title";
    public static String sSortBy = "document_num";
    public static String sSortType = "desc";
    public static int sMemberNum = -1;
    public static String sQuoryMode = "general";

    public static String getQuery() {
        String r = "limit_count=" + Integer.toString(sLimitCount)
                + "&q=" + sKeyword
                + "&q_type=" + sKeywordType
                + "&sort_by=" + sSortBy
                + "&sort_type=" + sSortType
                + "&member_num=" + ((sMemberNum >= 0) ? Integer.toString(sMemberNum) : "")
                + "&q_mode=" + sQuoryMode;
        return r; //URLEncoder.encode(r);
    }
}
