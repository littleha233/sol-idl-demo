package org.example.project.dto;

public class BuildTxReq<T> {
    private String from;
    private T ext;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public T getExt() {
        return ext;
    }

    public void setExt(T ext) {
        this.ext = ext;
    }
}
