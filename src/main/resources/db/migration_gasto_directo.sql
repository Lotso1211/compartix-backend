-- ============================================================
-- Migración: nuevo tipo de movimiento GASTO_DIRECTO
-- Ejecutar en PostgreSQL (base compartix_db) ANTES de reiniciar
-- el backend, porque la DDL está en modo "validate".
-- ============================================================

ALTER TABLE movimientos DROP CONSTRAINT IF EXISTS movimientos_tipo_check;
ALTER TABLE movimientos ADD CONSTRAINT movimientos_tipo_check
  CHECK (tipo::text = ANY (ARRAY['APORTE','GASTO_COMPARTIDO','GASTO_INDIVIDUAL','MULTA','INGRESO_DIRECTO','GASTO_DIRECTO']::text[]));
