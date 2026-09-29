-- ============================================================
-- Migración: solicitudes de pago (comprobante → notificación → aprobación)
-- Ejecutar en PostgreSQL (base compartix_db) ANTES de reiniciar
-- el backend, porque la DDL está en modo "validate".
-- ============================================================

CREATE TABLE solicitudes_pago (
    id              BIGSERIAL PRIMARY KEY,
    grupo_id        BIGINT NOT NULL REFERENCES grupos(id),
    usuario_id      BIGINT NOT NULL REFERENCES usuarios(id),
    tipo            VARCHAR(20) NOT NULL CHECK (tipo IN ('CUOTA', 'MULTA')),
    cuota_id        BIGINT REFERENCES cuotas_programadas(id) ON DELETE CASCADE,
    multa_id        BIGINT REFERENCES multas(id) ON DELETE CASCADE,
    monto           NUMERIC(10, 2) NOT NULL,
    comprobante_url VARCHAR(500) NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    motivo_rechazo  TEXT,
    revisado_por    BIGINT REFERENCES usuarios(id),
    fecha_revision  TIMESTAMP,
    creado_en       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT solicitudes_pago_ref_check CHECK (
        (tipo = 'CUOTA' AND cuota_id IS NOT NULL AND multa_id IS NULL) OR
        (tipo = 'MULTA' AND multa_id IS NOT NULL AND cuota_id IS NULL)
    )
);

CREATE INDEX idx_solicitudes_pago_grupo ON solicitudes_pago(grupo_id);
CREATE INDEX idx_solicitudes_pago_usuario ON solicitudes_pago(usuario_id, grupo_id);
CREATE INDEX idx_solicitudes_pago_cuota ON solicitudes_pago(cuota_id);
CREATE INDEX idx_solicitudes_pago_multa ON solicitudes_pago(multa_id);

-- Nuevo tipo de notificación SOLICITUD_PAGO
ALTER TABLE notificaciones DROP CONSTRAINT IF EXISTS notificaciones_tipo_check;
ALTER TABLE notificaciones ADD CONSTRAINT notificaciones_tipo_check
  CHECK (tipo::text = ANY (ARRAY['RECORDATORIO','APORTE_REGISTRADO','MULTA','GASTO','SISTEMA',
                                  'MORA_CUOTA','FONDO_BAJO','GRUPO','PEDIDO','SOLICITUD_PAGO']::text[]));
