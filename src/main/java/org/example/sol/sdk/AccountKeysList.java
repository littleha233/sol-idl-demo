package org.example.sol.sdk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AccountKeysList {
    private final Map<String, Flags> merged = new LinkedHashMap<String, Flags>();

    public void addAll(List<AccountMeta> list) {
        if (list == null) {
            return;
        }
        for (AccountMeta accountMeta : list) {
            add(accountMeta);
        }
    }

    public void add(AccountMeta accountMeta) {
        if (accountMeta == null) {
            return;
        }
        String key = accountMeta.getPublicKey().toBase58();
        Flags flags = merged.get(key);
        if (flags == null) {
            flags = new Flags(accountMeta.getPublicKey(), accountMeta.isSigner(), accountMeta.isWritable(), merged.size());
            merged.put(key, flags);
            return;
        }
        flags.signer = flags.signer || accountMeta.isSigner();
        flags.writable = flags.writable || accountMeta.isWritable();
    }

    public List<AccountMeta> getList() {
        List<Flags> flagsList = new ArrayList<Flags>(merged.values());
        flagsList.sort(new Comparator<Flags>() {
            @Override
            public int compare(Flags a, Flags b) {
                int ga = group(a);
                int gb = group(b);
                if (ga != gb) {
                    return Integer.compare(ga, gb);
                }
                return Integer.compare(a.order, b.order);
            }
        });

        List<AccountMeta> out = new ArrayList<AccountMeta>(flagsList.size());
        for (Flags flags : flagsList) {
            out.add(new AccountMeta(flags.publicKey, flags.signer, flags.writable));
        }
        return out;
    }

    private int group(Flags f) {
        if (f.signer) {
            return f.writable ? 0 : 1;
        }
        return f.writable ? 2 : 3;
    }

    private static class Flags {
        private final PublicKey publicKey;
        private boolean signer;
        private boolean writable;
        private final int order;

        private Flags(PublicKey publicKey, boolean signer, boolean writable, int order) {
            this.publicKey = publicKey;
            this.signer = signer;
            this.writable = writable;
            this.order = order;
        }
    }
}
