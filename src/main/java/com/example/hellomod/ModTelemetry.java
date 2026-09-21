package com.example.hellomod;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/** Local mod telemetry, including AI prompts, snapshots and model responses. */
public final class ModTelemetry implements AutoCloseable {
    private final OpenTelemetrySdk sdk;
    private final LongCounter joins;
    private final LongCounter commands;

    public ModTelemetry(String endpoint) {
        String base = endpoint.replaceAll("/+$", "");
        Resource resource = Resource.create(Attributes.of(
            AttributeKey.stringKey("service.name"), "minecraft-hello-mod",
            AttributeKey.stringKey("service.version"), "1.0.0",
            AttributeKey.stringKey("minecraft.version"), "1.21.1"));
        var traces = SdkTracerProvider.builder().setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(OtlpHttpSpanExporter.builder()
                .setEndpoint(base + "/v1/traces").setTimeout(Duration.ofSeconds(2)).build())
                .setScheduleDelay(Duration.ofSeconds(1)).build()).build();
        var logs = SdkLoggerProvider.builder().setResource(resource)
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(OtlpHttpLogRecordExporter.builder()
                .setEndpoint(base + "/v1/logs").setTimeout(Duration.ofSeconds(2)).build())
                .setScheduleDelay(Duration.ofSeconds(1)).build()).build();
        var metrics = SdkMeterProvider.builder().setResource(resource)
            // Keep this starter focused on mod metrics. Aspire 13.5 rejects some
            // SDK self-monitoring histograms that have no explicit bounds.
            .registerView(io.opentelemetry.sdk.metrics.InstrumentSelector.builder()
                .setName("otel.sdk.*").build(), io.opentelemetry.sdk.metrics.View.builder()
                .setAggregation(io.opentelemetry.sdk.metrics.Aggregation.drop()).build())
            .registerMetricReader(PeriodicMetricReader.builder(OtlpHttpMetricExporter.builder()
                .setEndpoint(base + "/v1/metrics").setTimeout(Duration.ofSeconds(2)).build())
                .setInterval(Duration.ofSeconds(5)).build()).build();
        sdk = OpenTelemetrySdk.builder().setTracerProvider(traces)
            .setLoggerProvider(logs).setMeterProvider(metrics).build();
        var meter = sdk.getMeter("hellomod");
        joins = meter.counterBuilder("hellomod.player.joins")
            .setDescription("Number of player joins since game launch").build();
        commands = meter.counterBuilder("hellomod.command.executions")
            .setDescription("Number of /hellomod invocations since game launch").build();
    }

    public void log(String message) {
        sdk.getLogsBridge().get("hellomod").logRecordBuilder()
            .setSeverity(Severity.INFO).setBody(message).emit();
    }

    public Span startAiRequest() {
        return sdk.getTracer("hellomod").spanBuilder("ai.askmod").setNoParent()
            .setSpanKind(io.opentelemetry.api.trace.SpanKind.CLIENT)
            .setAttribute("gen_ai.operation.name", "chat")
            .setAttribute("gen_ai.provider.name", "ollama")
            .setAttribute("gen_ai.request.model", OllamaAssistant.MODEL)
            .startSpan();
    }

    public void playerJoined() {
        joins.add(1);
        log("Player joined the world");
    }

    void playerMoved(int entityId, MovementTracker.Movement movement) {
        var from = movement.from();
        var to = movement.to();
        Span span = sdk.getTracer("hellomod").spanBuilder("player.move").setNoParent().startSpan();
        try {
            span.setAttribute("player.entity.id", entityId);
            span.setAttribute("player.position.previous.x", from.x());
            span.setAttribute("player.position.previous.y", from.y());
            span.setAttribute("player.position.previous.z", from.z());
            span.setAttribute("player.position.x", to.x());
            span.setAttribute("player.position.y", to.y());
            span.setAttribute("player.position.z", to.z());
            span.setAttribute("player.dimension.previous", from.dimension());
            span.setAttribute("player.dimension", to.dimension());
            span.setAttribute("player.dimension.changed", movement.dimensionChanged());
            if (!movement.dimensionChanged()) {
                span.setAttribute("player.movement.distance", movement.distance());
            }
        } finally {
            span.end();
        }
    }

    public int command(IntSupplier action) {
        Span span = sdk.getTracer("hellomod").spanBuilder("hellomod.command").startSpan();
        try (Scope scope = span.makeCurrent()) {
            commands.add(1);
            int result = action.getAsInt();
            log("/hellomod executed");
            return result;
        } catch (RuntimeException error) {
            span.recordException(error);
            span.setStatus(StatusCode.ERROR);
            throw error;
        } finally {
            span.end();
        }
    }

    public void flush() {
        sdk.getSdkTracerProvider().forceFlush().join(5, TimeUnit.SECONDS);
        sdk.getSdkLoggerProvider().forceFlush().join(5, TimeUnit.SECONDS);
        sdk.getSdkMeterProvider().forceFlush().join(5, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        sdk.close();
    }
}
