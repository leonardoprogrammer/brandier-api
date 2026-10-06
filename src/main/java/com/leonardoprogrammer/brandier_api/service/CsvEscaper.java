package com.leonardoprogrammer.brandier_api.service;

import java.util.List;

public final class CsvEscaper {

    public static final String SEPARADOR = ";";
    public static final String FIM_DE_LINHA = "\r\n";

    private static final String INICIOS_PERIGOSOS = "=+-@\t\r";

    private CsvEscaper() {
    }

    public static String campo(String valor) {
        if (valor == null) {
            return "";
        }

        String texto = valor;
        if (!texto.isEmpty() && INICIOS_PERIGOSOS.indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.contains(SEPARADOR) || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    public static String linha(List<String> campos) {
        return String.join(SEPARADOR, campos.stream().map(CsvEscaper::campo).toList()) + FIM_DE_LINHA;
    }
}
