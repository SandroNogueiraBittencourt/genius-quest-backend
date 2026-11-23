"""Testes offline das protecoes do utilitario V20; nunca conectam ao Render."""
import importlib.util
import io
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, call

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

    def test_sql_tem_delimitadores_corretos_e_preserva_v13(self):
        self.assertIn("DO $$", mod.VERIFY_SQL)
        self.assertIn("END $$;", mod.VERIFY_SQL)
        self.assertNotIn("DO $\\n", mod.VERIFY_SQL)
        self.assertNotIn("END $;", mod.VERIFY_SQL)
        self.assertIn("origem NOT IN (12, 13)", mod.VERIFY_SQL)
        self.assertIn("20 - origem", mod.VERIFY_SQL)
        self.assertIn("pergunta_atual", mod.VERIFY_SQL)

    def test_backup_v13_cria_dump_e_manifesto_em_diretorio_privado(self):
        with tempfile.TemporaryDirectory() as d, patch.object(mod, "BACKUP_DIR", Path(d)):
            with patch.object(mod, "require_bin"), patch.object(mod, "read_db_conn",
                    return_value=({"PGPASSWORD": "somente_teste"},
                        ["-h", "db.example.test", "-p", "5432", "-U", "u", "-d", "db"])):
                def fake_command(args, **kwargs):
                    if args[0] == "psql":
                        return subprocess.CompletedProcess(args, 0, stdout="13\n" if "MAX(version" in args[-1] else "0\n")
                    if args[0] == "pg_dump":
                        Path(args[args.index("--file") + 1]).write_bytes(b"dump_sintetico")
                    return subprocess.CompletedProcess(args, 0, stdout="ok")
                with patch.object(mod, "command", side_effect=fake_command):
                    with patch("sys.stdout", new_callable=io.StringIO):
                        mod.backup()
            dumps = list(Path(d).glob("*.dump"))
            self.assertEqual(1, len(dumps))
            self.assertEqual(1, len(list(Path(d).glob("*.dump.sha256"))))
            self.assertEqual(mod.sha256(dumps[0]), dumps[0].with_suffix(".dump.sha256").read_text().split()[0])

    def test_backup_recusa_versao_nao_homologada_antes_de_pg_dump(self):
        with tempfile.TemporaryDirectory() as d, patch.object(mod, "BACKUP_DIR", Path(d)):
            with patch.object(mod, "require_bin"), patch.object(mod, "read_db_conn",
                    return_value=({"PGPASSWORD": "somente_teste"}, ["-h", "test"])):
                with patch.object(mod, "command",
                    return_value=subprocess.CompletedProcess(["psql"], 0, stdout="14\n")) as executar:
                    with patch("sys.stdout", new_callable=io.StringIO):
                        with self.assertRaises(mod.ValidacaoError):
                            mod.backup()
                self.assertEqual(1, executar.call_count)
                self.assertEqual("psql", executar.call_args.args[0][0])

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
