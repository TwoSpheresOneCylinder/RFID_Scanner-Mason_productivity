package com.mason.bricktracking.data.remote;

import java.util.List;

public class CompaniesResponse {
    private boolean success;
    private List<Company> companies;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public List<Company> getCompanies() { return companies; }
    public void setCompanies(List<Company> companies) { this.companies = companies; }
}
