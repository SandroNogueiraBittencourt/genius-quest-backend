package br.com.geniusquest.service;

import java.security.SecureRandom;
import java.util.Locale;

/** Codigo aleatorio de 80 bits, para digitacao manual. Nenhum segredo em links. */
public final class CodigoRecuperacao {
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigoRecuperacao() {}

    public static String gerar() {
        byte[] dados = new byte[10]; // 80 bits de entropia
        RANDOM.nextBytes(dados);
        StringBuilder resultado = new StringBuilder(16);
        int buffer = 0;
        int bits = 0;
        for (byte dado : dados) {
            buffer = (buffer << 8) | (dado & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                resultado.append(ALFABETO.charAt((buffer >>> bits) & 31));
                buffer &= (1 << bits) - 1;
            }
        }
        return resultado.toString();
    }

    public static String normalizar(String codigo) {
        if (codigo == null) return "";
        return codigo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean formatoValido(String codigo) {
        String normalizado = normalizar(codigo);
        if (normalizado.length() != 16) return false;
        for (int i = 0; i < normalizado.length(); i++) {
            if (ALFABETO.indexOf(normalizado.charAt(i)) < 0) return false;
        }
        return true;
    }

    public static String paraExibicao(String codigo) {
        return codigo.substring(0, 4) + "-" + codigo.substring(4, 8) + "-"
                + codigo.substring(8, 12) + "-" + codigo.substring(12, 16);
    }
}
