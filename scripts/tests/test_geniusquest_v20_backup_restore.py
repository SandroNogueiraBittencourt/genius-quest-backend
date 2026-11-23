"""Testes offline das protecoes do utilitario V20; nunca conectam ao Render."""
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "geniusquest_v20_backup_restore.py"
SPEC = importlib.util.spec_from_file_location("gq_v20", SCRIPT)
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)


class BackupRestoreSafetyTests(unittest.TestCase):
    def test_url_senha_apenas_no_ambiente(self):
        url = "postgresql://genius:Minha%40Senha@db.example.test:5432/genius_prod?sslmode=require"
        with patch.object(mod.getpass, "getpass", return_value=url):
            with patch("sys.stdout", new_callable=io.StringIO):
                env, conn = mod.read_db_conn()
        self.assertEqual(env["PGPASSWORD"], "Minha@Senha")
        self.assertEqual(env["PGSSLMODE"], "require")
        self.assertEqual(conn, ["-h", "db.example.test", "-p", "5432",
                                "-U", "genius", "-d", "genius_prod"])
        self.assertNotIn("Minha", " ".join(conn))

    def test_url_sem_tls_explicitamente_rejeitada(self):
        with patch.object(mod.getpass, "getpass", return_value=
                          "postgres://a:b@db.example.test/test?sslmode=disable"):
            with patch("sys.stdout", new_callable=io.StringIO):
                with self.assertRaises(mod.ValidacaoError):
                    mod.read_db_conn()

    def test_url_exige_usuario_senha_host_e_db(self):
        for url in ("https://db.example.test", "postgresql://a@db.example.test/db",
                    "postgresql://a:b@db.example.test/"):
            with self.subTest(url=url):
                with patch.object(mod.getpass, "getpass", return_value=url):
                    with patch("sys.stdout", new_callable=io.StringIO):
                        with self.assertRaises(mod.ValidacaoError):
                            mod.read_db_conn()

    def test_hash_validado_antes_de_criar_containers(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            archive = root / "teste.dump"
            archive.write_bytes(b"dump_integro")
            (root / "teste.dump.sha256").write_text("0" * 64 + "  teste.dump\n")
            migrations = root / "migrations"
            migrations.mkdir()
            (migrations / "V20__encerramento_da_sala.sql").write_text(
                "-- migration de teste\n")
            with patch.object(mod, "require_bin"):
                with patch.object(mod, "command") as command:
                    with self.assertRaises(mod.ValidacaoError):
                        mod.restore_test(archive, migrations)
                    command.assert_not_called()


if __name__ == "__main__":
    unittest.main()
