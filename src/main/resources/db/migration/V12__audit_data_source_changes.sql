CREATE TRIGGER audit_log_data_sources
AFTER INSERT OR UPDATE OR DELETE ON data_sources
FOR EACH ROW EXECUTE FUNCTION record_audit_change('id');
