package com.example.hackathon;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SharedPreferences를 사용해 등록된 기업 목록을 관리하는 헬퍼 클래스
 */
public class CompanyStorage {

    private static final String PREFS_NAME = "CompanyPrefs";
    private static final String KEY_COMPANY_SET = "CompanySet";

    private SharedPreferences prefs;

    public CompanyStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 새로운 기업 이름을 목록에 추가합니다.
     */
    public void addCompany(String companyName) {
        Set<String> companySet = getCompanySet();
        companySet.add(companyName);
        prefs.edit().putStringSet(KEY_COMPANY_SET, companySet).apply();
    }

    /**
     * 저장된 모든 기업 목록을 가져옵니다.
     */
    public List<String> getCompanyList() {
        Set<String> companySet = getCompanySet();
        // 기본값으로 '병무청'을 항상 포함 (서버에 '예비군편성.txt'가 기본일 경우)
        if (!companySet.contains("병무청")) {
            companySet.add("병무청");
        }
        return new ArrayList<>(companySet);
    }

    private Set<String> getCompanySet() {
        // new HashSet<>()을 해줘야 수정 가능한 Set이 반환됩니다.
        return new HashSet<>(prefs.getStringSet(KEY_COMPANY_SET, new HashSet<>()));
    }
}