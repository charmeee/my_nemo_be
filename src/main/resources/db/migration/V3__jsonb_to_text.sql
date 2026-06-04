-- jsonb 컬럼을 text로 변경 (Hibernate String 매핑 호환성)
ALTER TABLE tldraw_documents ALTER COLUMN state TYPE TEXT USING state::TEXT;
ALTER TABLE notifications ALTER COLUMN payload TYPE TEXT USING payload::TEXT;
ALTER TABLE trash ALTER COLUMN original_data TYPE TEXT USING original_data::TEXT;
