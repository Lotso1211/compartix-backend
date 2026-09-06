-- ============================================================
-- Migración: anular mensualidades de un pago programado
-- Ejecutar en PostgreSQL (base compartix_db) ANTES de reiniciar
-- el backend, porque la DDL está en modo "validate".
-- ============================================================

ALTER TABLE cuotas_programadas ADD COLUMN IF NOT EXISTS motivo_anulacion VARCHAR(255);

ALTER TABLE cuotas_programadas DROP CONSTRAINT IF EXISTS chk_estado_cuota;
ALTER TABLE cuotas_programadas ADD CONSTRAINT chk_estado_cuota
  CHECK (estado::text = ANY (ARRAY['PENDIENTE','PAGADA','VENCIDA','CONGELADA','ANULADA']::text[]));
