package com.kuna.bgmstoremobile;

public class BGMStoreQuery {
    private static int sLimitCount = 0;
    private static String sKeyword = "";
    private static String sKeywordType = "title";
    private static String sSortBy = "document_num";
    private static String sSortType = "desc";
    private static int sMemberNum = -1;
    private static String sQuoryMode = "general";

    public static void setKeyword(String keyword) {
        sKeyword = keyword;
    }
    
    public static int getLimitCount() {
    	return sLimitCount;
    }
    
    public static void setLimitCount(int cnt) {
        sLimitCount = cnt;
    }

    public static String getQuery() {
        return "limit_count=" + Integer.toString(sLimitCount)
                + "&q=" + sKeyword
                + "&q_type=" + sKeywordType
                + "&sort_by=" + sSortBy
                + "&sort_type=" + sSortType
                + "&member_num=" + ((sMemberNum >= 0) ? Integer.toString(sMemberNum) : "")
                + "&q_mode=" + sQuoryMode;
        //URLEncoder.encode(r);
    }
}
