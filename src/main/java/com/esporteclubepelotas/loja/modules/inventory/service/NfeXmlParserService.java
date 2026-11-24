package com.esporteclubepelotas.loja.modules.inventory.service;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class NfeXmlParserService {

    public record ParsedNfe(
            String invoiceNumber,
            String invoiceSeries,
            String accessKey44,
            String supplierCnpj,
            String supplierName,
            BigDecimal totalCost,
            List<ParsedNfeItem> items
    ) {}

    public record ParsedNfeItem(
            int lineNumber,
            String supplierProductCode,
            String eanBarcode,
            String productName,
            String ncm,
            int quantity,
            BigDecimal unitCost,
            BigDecimal totalCost
    ) {}

    public ParsedNfe parse(byte[] xmlBytes) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            throw new BusinessException("Arquivo XML vazio.");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);

            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();

            Element infNFe = firstElementByLocalName(doc.getDocumentElement(), "infNFe");
            if (infNFe == null) {
                throw new BusinessException("XML inválido: tag <infNFe> não encontrada.");
            }

            String accessKey = readText(doc.getDocumentElement(), "chNFe");
            if (accessKey == null || accessKey.isBlank()) {
                String idAttr = infNFe.getAttribute("Id");
                accessKey = idAttr != null ? idAttr.replaceFirst("^NFe", "").trim() : null;
            }
            if (accessKey != null && accessKey.length() > 44) {
                accessKey = accessKey.substring(0, 44);
            }

            Element ide = firstElementByLocalName(infNFe, "ide");
            String invoiceNumber = ide != null ? readText(ide, "nNF") : null;
            String invoiceSeries = ide != null ? readText(ide, "serie") : null;

            Element emit = firstElementByLocalName(infNFe, "emit");
            String supplierCnpj = emit != null ? normalizeDigits(readText(emit, "CNPJ")) : null;
            String supplierName = emit != null ? readText(emit, "xNome") : "Fornecedor não informado";

            NodeList detNodes = infNFe.getElementsByTagNameNS("*", "det");
            if (detNodes.getLength() == 0) {
                detNodes = infNFe.getElementsByTagName("det");
            }
            if (detNodes.getLength() == 0) {
                throw new BusinessException("Nenhum item (<det>) encontrado na NF-e.");
            }

            List<ParsedNfeItem> items = new ArrayList<>();
            BigDecimal computedTotal = BigDecimal.ZERO;

            for (int i = 0; i < detNodes.getLength(); i++) {
                Element det = (Element) detNodes.item(i);
                Element prod = firstElementByLocalName(det, "prod");
                if (prod == null) continue;

                String cProd = readText(prod, "cProd");
                String rawEan = readText(prod, "cEAN");
                String cleanEan = (rawEan == null || rawEan.isBlank() || "SEM GTIN".equalsIgnoreCase(rawEan.trim()))
                        ? null
                        : rawEan.trim();
                String xProd = readText(prod, "xProd");
                String ncm = readText(prod, "NCM");

                BigDecimal qCom = parseDecimal(readText(prod, "qCom"));
                int qty = qCom.intValue();
                if (qty <= 0) {
                    throw new BusinessException("Quantidade inválida no item " + (i + 1) + " da NF-e.");
                }

                BigDecimal unitCost = parseDecimal(readText(prod, "vUnCom"));
                BigDecimal itemTotal = parseDecimal(readText(prod, "vProd"));
                if (itemTotal.compareTo(BigDecimal.ZERO) == 0 && unitCost.compareTo(BigDecimal.ZERO) > 0) {
                    itemTotal = unitCost.multiply(BigDecimal.valueOf(qty));
                }

                computedTotal = computedTotal.add(itemTotal);
                items.add(new ParsedNfeItem(
                        i + 1,
                        cProd != null ? cProd.trim() : null,
                        cleanEan,
                        xProd != null ? xProd.trim() : "Produto sem descrição",
                        ncm != null ? ncm.trim() : null,
                        qty,
                        unitCost,
                        itemTotal
                ));
            }

            return new ParsedNfe(
                    invoiceNumber,
                    invoiceSeries,
                    accessKey != null && !accessKey.isBlank() ? accessKey : null,
                    supplierCnpj,
                    supplierName,
                    computedTotal,
                    items
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Não foi possível ler o XML da NF-e: " + ex.getMessage());
        }
    }

    private static Element firstElementByLocalName(Element root, String localName) {
        if (root == null) return null;
        if (localName.equals(root.getLocalName()) || localName.equals(root.getTagName())) {
            return root;
        }
        NodeList list = root.getElementsByTagNameNS("*", localName);
        if (list.getLength() == 0) {
            list = root.getElementsByTagName(localName);
        }
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static String readText(Element root, String localName) {
        Element el = firstElementByLocalName(root, localName);
        return el != null && el.getTextContent() != null ? el.getTextContent().trim() : null;
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(raw.trim().replace(",", "."));
    }

    private static String normalizeDigits(String raw) {
        return raw != null ? raw.replaceAll("[^0-9]", "") : null;
    }
}
