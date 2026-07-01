package com.ripple.planner.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.*;

import java.io.IOException;

/**
 * JTS Geometry 的 GeoJSON 序列化器。
 * <p>
 * Jackson 默认会将 JTS Geometry 对象当作普通 Java Bean 序列化，导致输出大量内部属性
 * （如 envelopeInternal、factory、SRID 等），而不是标准的 GeoJSON 格式。
 * 本序列化器将 Geometry 对象转换为符合 RFC 7946 标准的 GeoJSON，便于前端地图组件
 * （如 Leaflet、Mapbox、Cesium）直接解析和渲染。
 * </p>
 */
public class GeometryJsonSerializer extends JsonSerializer<Geometry> {

    @Override
    public void serialize(Geometry value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        writeGeometry(gen, value);
    }

    private void writeGeometry(JsonGenerator gen, Geometry geom) throws IOException {
        if (geom instanceof GeometryCollection) {
            writeGeometryCollection(gen, (GeometryCollection) geom);
            return;
        }

        gen.writeStartObject();
        gen.writeStringField("type", geom.getGeometryType());
        gen.writeFieldName("coordinates");
        writeCoordinates(gen, geom);
        gen.writeEndObject();
    }

    private void writeCoordinates(JsonGenerator gen, Geometry geom) throws IOException {
        if (geom instanceof Point) {
            writePoint(gen, (Point) geom);
        } else if (geom instanceof LineString) {
            writeLineString(gen, (LineString) geom);
        } else if (geom instanceof Polygon) {
            writePolygon(gen, (Polygon) geom);
        } else if (geom instanceof MultiPoint) {
            writeMultiPoint(gen, (MultiPoint) geom);
        } else if (geom instanceof MultiLineString) {
            writeMultiLineString(gen, (MultiLineString) geom);
        } else if (geom instanceof MultiPolygon) {
            writeMultiPolygon(gen, (MultiPolygon) geom);
        } else {
            gen.writeNull();
        }
    }

    private void writePoint(JsonGenerator gen, Point p) throws IOException {
        gen.writeStartArray();
        gen.writeNumber(p.getX());
        gen.writeNumber(p.getY());
        gen.writeEndArray();
    }

    private void writeLineString(JsonGenerator gen, LineString ls) throws IOException {
        gen.writeStartArray();
        for (Coordinate c : ls.getCoordinates()) {
            gen.writeStartArray();
            gen.writeNumber(c.x);
            gen.writeNumber(c.y);
            gen.writeEndArray();
        }
        gen.writeEndArray();
    }

    private void writePolygon(JsonGenerator gen, Polygon poly) throws IOException {
        gen.writeStartArray();
        writeLineString(gen, poly.getExteriorRing());
        for (int i = 0; i < poly.getNumInteriorRing(); i++) {
            writeLineString(gen, poly.getInteriorRingN(i));
        }
        gen.writeEndArray();
    }

    private void writeMultiPoint(JsonGenerator gen, MultiPoint mp) throws IOException {
        gen.writeStartArray();
        for (int i = 0; i < mp.getNumGeometries(); i++) {
            writePoint(gen, (Point) mp.getGeometryN(i));
        }
        gen.writeEndArray();
    }

    private void writeMultiLineString(JsonGenerator gen, MultiLineString mls) throws IOException {
        gen.writeStartArray();
        for (int i = 0; i < mls.getNumGeometries(); i++) {
            writeLineString(gen, (LineString) mls.getGeometryN(i));
        }
        gen.writeEndArray();
    }

    private void writeMultiPolygon(JsonGenerator gen, MultiPolygon mp) throws IOException {
        gen.writeStartArray();
        for (int i = 0; i < mp.getNumGeometries(); i++) {
            writePolygon(gen, (Polygon) mp.getGeometryN(i));
        }
        gen.writeEndArray();
    }

    private void writeGeometryCollection(JsonGenerator gen, GeometryCollection gc) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", "GeometryCollection");
        gen.writeArrayFieldStart("geometries");
        for (int i = 0; i < gc.getNumGeometries(); i++) {
            writeGeometry(gen, gc.getGeometryN(i));
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
