-- ============================================================
-- Migración: cupo de carnaval (meta en USD), Fondo Común, reparto editable
-- Ejecutar en PostgreSQL (base compartix_db) ANTES de reiniciar
-- el backend, porque la DDL está en modo "validate".
-- ============================================================

ALTER TABLE pagos_programados ADD COLUMN IF NOT EXISTS cupo_carnaval_usd NUMERIC(10,2);
ALTER TABLE pagos_programados ADD COLUMN IF NOT EXISTS tipo_cambio NUMERIC(10,4);
ALTER TABLE pagos_programados ADD COLUMN IF NOT EXISTS meses_restantes_carnaval INTEGER;

ALTER TABLE saldo_grupo ADD COLUMN IF NOT EXISTS saldo_comun NUMERIC(10,2) NOT NULL DEFAULT 0;

-- Nuevo valor COMUN en el fondo de los movimientos (Ingreso Directo / Gasto Directo).
ALTER TABLE movimientos DROP CONSTRAINT IF EXISTS chk_fondo_movimiento;
ALTER TABLE movimientos ADD CONSTRAINT chk_fondo_movimiento
  CHECK (fondo IS NULL OR fondo::text = ANY (ARRAY['CARNAVAL','AHORRO','MIXTO','COMUN']::text[]));
