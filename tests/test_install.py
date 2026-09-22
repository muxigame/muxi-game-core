"""Installer tests use temporary synthetic servers and never touch a real pack."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import install


class InstallTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.repo = self.root / 'repo'
        self.server = self.root / 'server'
        (self.repo / 'build/libs').mkdir(parents=True)
        (self.repo / 'config-examples').mkdir()
        (self.server / 'mods').mkdir(parents=True)
        (self.server / 'config').mkdir()
        (self.server / 'server.properties').write_text('online-mode=false\n')
        (self.server / 'world').mkdir()
        (self.server / 'world/keep.dat').write_bytes(b'do not touch player data')
        self.jar = self.repo / 'build/libs/muxi-game-core-1.0.0.jar'
        with zipfile.ZipFile(self.jar, 'w') as jar:
            jar.writestr('META-INF/neoforge.mods.toml', 'modId="muxi_game_core"\nversion="1.0.0"')
        (self.repo / 'build/release.json').write_text(json.dumps({
            'version': '1.0.0', 'artifact': self.jar.name,
            'sha256': hashlib.sha256(self.jar.read_bytes()).hexdigest(),
            'size': self.jar.stat().st_size,
        }))
        (self.repo / 'start-muxi.ps1').write_text('# synthetic start script\n')
        (self.repo / 'config-examples/muxi-game-core.json').write_text(json.dumps({
            'schema': 1, 'features': {'identity': {'enabled': False}}
        }))
        (self.server / 'mods/simplenicknames-test.jar').write_bytes(b'nickname dependency')
        (self.repo / 'dependencies.json').write_text(json.dumps({'simpleNicknames': {
            'filename': 'simplenicknames-test.jar',
            'sha512': hashlib.sha512(b'nickname dependency').hexdigest(),
        }}))

    def tearDown(self):
        self.temp.cleanup()

    def snapshot(self):
        return {p.relative_to(self.server).as_posix(): p.read_bytes()
                for p in self.server.rglob('*') if p.is_file() and '.muxi-game-core-backups' not in p.parts}

    def legacy(self):
        old = {'endpoint': 'https://account.example.com/identity/',
               'serverKey': 'test-secret-' * 4, 'refreshSeconds': 60}
        (self.server / install.LEGACY_NAME).write_text(json.dumps(old))
        (self.server / 'mods/muxi-identity-1.0.0.jar').write_bytes(b'old module')
        return old

    def test_dry_run_does_not_mutate_server(self):
        self.legacy(); before = self.snapshot()
        result = install.install(self.server, True, self.repo)
        self.assertTrue(result['dryRun'])
        self.assertEqual(before, self.snapshot())

    def test_legacy_migration_and_private_backup(self):
        legacy = self.legacy()
        result = install.install(self.server, repo=self.repo)
        config = json.loads((self.server / install.CONFIG_NAME).read_text())
        self.assertTrue(config['features']['identity']['enabled'])
        self.assertEqual(legacy['serverKey'], config['features']['identity']['serverKey'])
        self.assertNotIn(legacy['serverKey'], json.dumps(result))
        self.assertFalse((self.server / 'mods/muxi-identity-1.0.0.jar').exists())
        self.assertFalse((self.server / install.LEGACY_NAME).exists())
        self.assertTrue((Path(result['backup']) / install.LEGACY_NAME).exists())
        self.assertEqual(b'do not touch player data', (self.server / 'world/keep.dat').read_bytes())

    def test_reinstall_is_idempotent_and_keeps_custom_config(self):
        self.legacy(); install.install(self.server, repo=self.repo)
        path = self.server / install.CONFIG_NAME
        data = json.loads(path.read_text()); data['features']['identity']['refreshSeconds'] = 120
        path.write_text(json.dumps(data))
        before = self.snapshot()
        result = install.install(self.server, repo=self.repo)
        self.assertEqual([], result['changed'])
        self.assertEqual(before, self.snapshot())

    def test_missing_config_creates_disabled_offline_core(self):
        result = install.install(self.server, repo=self.repo)
        self.assertFalse(result['identityEnabled'])
        self.assertFalse(json.loads((self.server / install.CONFIG_NAME).read_text())['features']['identity']['enabled'])

    def test_bad_dependency_fails_before_mutations(self):
        self.legacy()
        (self.server / 'mods/simplenicknames-test.jar').write_bytes(b'tampered')
        before = self.snapshot()
        with self.assertRaises(ValueError): install.install(self.server, repo=self.repo)
        self.assertEqual(before, self.snapshot())

    def test_bad_artifact_fails_before_mutations(self):
        self.jar.write_bytes(b'tampered')
        before = self.snapshot()
        with self.assertRaises(ValueError): install.install(self.server, repo=self.repo)
        self.assertEqual(before, self.snapshot())

    def test_failure_rolls_back_live_files(self):
        self.legacy(); before = self.snapshot()
        atomic = install.atomic_write
        fail_path = self.server / 'start-muxi.ps1'
        def sometimes_fail(path, content):
            if path == fail_path:
                raise OSError('synthetic write failure')
            return atomic(path, content)
        with patch.object(install, 'atomic_write', side_effect=sometimes_fail):
            with self.assertRaises(RuntimeError): install.install(self.server, repo=self.repo)
        self.assertEqual(before, self.snapshot())

    def test_invalid_private_input_is_not_echoed(self):
        secret = 'test-secret-' * 4
        for endpoint in ('http://example.com/', 'https://example.com/path?key=1/', 'https://u:p@example.com/'):
            with self.assertRaises(ValueError) as error:
                install.validate_config({'features': {'identity': {
                    'enabled': True, 'endpoint': endpoint, 'serverKey': secret}}})
            self.assertNotIn(secret, str(error.exception))


if __name__ == '__main__': unittest.main()
