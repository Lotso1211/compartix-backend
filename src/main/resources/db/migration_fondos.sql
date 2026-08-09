-- ============================================================
-- Migración: cuota dividida en Carnaval + Ahorro
-- Ejecutar en PostgreSQL (base compartix_db) ANTES de reiniciar
-- el backend, porque la DDL está en modo "validate".
-- ============================================================

-- Pago programado: monto dividido en carnaval + ahorro
ALTER TABLE pagos_programados ADD COLUMN IF NOT EXISTS monto_carnaval NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE pagos_programados ADD COLUMN IF NOT EXISTS monto_ahorro   NUMERIC(10,2) NOT NULL DEFAULT 0;
-- Los pagos existentes pasan a ser 100% carnaval
UPDATE pagos_programados SET monto_carnaval = monto_mensual WHERE monto_carnaval = 0;

-- Cuota: mismo desglose por mes
ALTER TABLE cuotas_programadas ADD COLUMN IF NOT EXISTS monto_carnaval NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE cuotas_programadas ADD COLUMN IF NOT EXISTS monto_ahorro   NUMERIC(10,2) NOT NULL DEFAULT 0;
UPDATE cuotas_programadas SET monto_carnaval = monto WHERE monto_carnaval = 0;

-- Kardex del miembro: total ahorrado (carnaval ya está en total_aportes)
ALTER TABLE kardex ADD COLUMN IF NOT EXISTS total_ahorro NUMERIC(10,2) NOT NULL DEFAULT 0;

-- Saldo del grupo: visibilidad por fondo
ALTER TABLE saldo_grupo ADD COLUMN IF NOT EXISTS saldo_carnaval NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE saldo_grupo ADD COLUMN IF NOT EXISTS saldo_ahorro   NUMERIC(10,2) NOT NULL DEFAULT 0;

-- Miembro: si participa o no en carnaval (puede pagar solo ahorro)
ALTER TABLE grupo_miembros ADD COLUMN IF NOT EXISTS participa_carnaval BOOLEAN NOT NULL DEFAULT TRUE;
