package com.nemo.nemo.domain.sync.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DiffApplier {

    @SuppressWarnings("unchecked")
    public void apply(Map<String, Object> state, Map<String, Object> diff) {
        if (diff == null) return;
        for (Map.Entry<String, Object> entry : diff.entrySet()) {
            String key = entry.getKey();
            List<Object> op = (List<Object>) entry.getValue();
            int opType = ((Number) op.get(0)).intValue();

            if (opType == 0) {
                // put: 전체 교체
                state.put(key, op.get(1));
            } else if (opType == 1) {
                // patch: 부분 수정
                Object existing = state.get(key);
                if (existing instanceof Map existingMap) {
                    Map<String, Object> patch = (Map<String, Object>) op.get(1);
                    existingMap.putAll(patch);
                } else {
                    state.put(key, op.get(1));
                }
            } else if (opType == 2) {
                // remove
                state.remove(key);
            }
        }
    }
}
