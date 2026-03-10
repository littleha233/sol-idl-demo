package org.example.coldidl.dto;

public class AccountFieldDto {
    private String name;
    private boolean signer;
    private boolean writable;
    private String source;
    private String valueHint;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSigner() {
        return signer;
    }

    public void setSigner(boolean signer) {
        this.signer = signer;
    }

    public boolean isWritable() {
        return writable;
    }

    public void setWritable(boolean writable) {
        this.writable = writable;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getValueHint() {
        return valueHint;
    }

    public void setValueHint(String valueHint) {
        this.valueHint = valueHint;
    }
}
