package com.classroom.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.classroom.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** TCH-15: renders the saved strokes (logical canvas 1280x720) to PNG or PDF. */
@Service
public class WhiteboardExportService {
    public static final int W = 1280, H = 720;

    public byte[] png(List<JsonNode> strokes) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        for (JsonNode s : strokes) {
            JsonNode pts = s.path("points");
            if (!"draw".equals(s.path("action").asText()) || pts.size() == 0) continue;
            int width = s.path("width").asInt(3);
            g.setColor(s.path("eraser").asBoolean() ? Color.WHITE : Color.decode(s.path("color").asText("#000000")));
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (pts.size() == 1) {
                double x = pts.get(0).get(0).asDouble(), y = pts.get(0).get(1).asDouble();
                g.fill(new java.awt.geom.Ellipse2D.Double(x - width / 2.0, y - width / 2.0, width, width));
            } else {
                for (int i = 1; i < pts.size(); i++) {
                    g.draw(new Line2D.Double(pts.get(i - 1).get(0).asDouble(), pts.get(i - 1).get(1).asDouble(),
                                             pts.get(i).get(0).asDouble(), pts.get(i).get(1).asDouble()));
                }
            }
        }
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not render board");
        }
    }

    public byte[] pdf(List<JsonNode> strokes) {
        byte[] png = png(strokes);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            com.lowagie.text.Document doc = new com.lowagie.text.Document(new com.lowagie.text.Rectangle(W, H), 0, 0, 0, 0);
            com.lowagie.text.pdf.PdfWriter.getInstance(doc, out);
            doc.open();
            com.lowagie.text.Image im = com.lowagie.text.Image.getInstance(png);
            im.setAbsolutePosition(0, 0);
            doc.add(im);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create PDF");
        }
    }
}
