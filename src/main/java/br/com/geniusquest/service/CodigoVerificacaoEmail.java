package br.com.geniusquest.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class CodigoVerificacaoEmail {
    static final long VALIDADE_MINUTOS = 15;
    private static final int DIGITOS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec chave;

    public CodigoVerificacaoEmail(@Value("${app.jwt.secret}") String segredoBase64) {
        chave = new SecretKeySpec(Base64.getDecoder().decode(segredoBase64), "HmacSHA256");
    }

    public static String gerar() {
        StringBuilder codigo = new StringBuilder(DIGITOS);
        for (int i = 0; i < DIGITOS; i++) {
            codigo.append(RANDOM.nextInt(10));
        }
        return codigo.toString();
    }

    public static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("[\\s-]", "");
    }

    public static boolean formatoValido(String codigo) {
        return normalizar(codigo).matches("[0-9]{" + DIGITOS + "}");
    }

    public String hash(String finalidade, String codigo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chave);
            return HexFormat.of().formatHex(
                    mac.doFinal((finalidade + ":" + normalizar(codigo)).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException erro) {
            throw new IllegalStateException("HMAC indisponível", erro);
        }
    }
}
