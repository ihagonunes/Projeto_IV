import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.security.crypto.bcrypt.BCrypt;

/** Development operator: uses the application's existing BCrypt dependency. */
class LocalMockPassword {
    public static void main(String[] args) throws Exception {
        String password = System.getenv("QSOFT_SETUP_MOCK_PASSWORD");
        if (password == null || password.length() < 8) throw new IllegalArgumentException("Senha mock ausente ou curta.");
        Path file = Path.of(args[0]);
        if (Files.exists(file)) {
            if (!BCrypt.checkpw(password, Files.readString(file).trim()))
                throw new IllegalArgumentException("Senha mock difere da execução anterior; não redefinir contas automaticamente.");
        } else {
            Files.writeString(file, BCrypt.hashpw(password, BCrypt.gensalt(12)), java.nio.file.StandardOpenOption.CREATE_NEW);
        }
    }
}
