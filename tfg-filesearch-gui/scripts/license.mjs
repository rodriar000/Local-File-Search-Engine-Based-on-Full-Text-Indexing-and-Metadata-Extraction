#!/usr/bin/env node
/**
 * Issues offline licences (see electron/license.ts for the format).
 *
 *   node scripts/license.mjs keygen --private <file>
 *       Creates the signing key pair once. The private key goes to <file> and must
 *       never be committed or shared: whoever has it can issue licences. The public
 *       key is written to electron/license-key.ts, which is committed and built in.
 *
 *   node scripts/license.mjs issue --private <file> --customer "Despacho Pérez" --seats 3
 *                                  [--expires 2027-12-31] [--id FS-2026-001] --out cliente.lic
 *       Signs a licence. Without --expires the licence never ends.
 *
 *   node scripts/license.mjs inspect <file.lic>
 *       Shows a licence's details and whether its signature matches the built-in key.
 */
import { generateKeyPairSync, createPrivateKey, createPublicKey, sign, verify, randomBytes } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const LICENSE_PREFIX = 'FSL1.'
const KEY_FILE = path.join(path.dirname(fileURLToPath(import.meta.url)), '../electron/license-key.ts')

/** Signs the licence details with an Ed25519 private key (PEM). */
export function issueLicense(privateKeyPem, details) {
    const payload = Buffer.from(JSON.stringify(details), 'utf-8').toString('base64url')
    const signed = LICENSE_PREFIX + payload
    const signature = sign(null, Buffer.from(signed, 'utf-8'), createPrivateKey(privateKeyPem)).toString('base64url')
    return `${signed}.${signature}`
}

export function publicKeyFileContents(publicKeyPem) {
    return [
        '/**',
        ' * Public half of the licence signing key, generated with "node scripts/license.mjs keygen".',
        ' * Only the private half can issue licences; it is kept outside the repository.',
        ' */',
        `export const LICENSE_PUBLIC_KEY = ${JSON.stringify(publicKeyPem)}`,
        '',
    ].join('\n')
}

function builtInPublicKey() {
    const match = /LICENSE_PUBLIC_KEY = (".*")/.exec(fs.readFileSync(KEY_FILE, 'utf-8'))
    return match ? JSON.parse(match[1]) : ''
}

function options(args) {
    const result = {}
    for (let i = 0; i < args.length; i++) {
        if (!args[i].startsWith('--')) throw new Error(`Unexpected argument: ${args[i]}`)
        const value = args[i + 1]
        if (value === undefined || value.startsWith('--')) throw new Error(`Missing value for ${args[i]}`)
        result[args[i].slice(2)] = value
        i++
    }
    return result
}

function today() {
    return new Date().toISOString().slice(0, 10)
}

function main([command, ...args]) {
    if (command === 'keygen') {
        const { private: privateFile } = options(args)
        if (!privateFile) throw new Error('Use --private <file> to say where to keep the private key.')
        if (fs.existsSync(privateFile)) throw new Error(`${privateFile} already exists; refusing to overwrite a signing key.`)
        const { publicKey, privateKey } = generateKeyPairSync('ed25519')
        fs.writeFileSync(privateFile, privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600, flag: 'wx' })
        fs.writeFileSync(KEY_FILE, publicKeyFileContents(publicKey.export({ type: 'spki', format: 'pem' })))
        console.log(`Private key saved to ${privateFile}. Back it up somewhere safe and never commit it.`)
        console.log(`Public key written to ${path.relative(process.cwd(), KEY_FILE)}; commit it and rebuild the app.`)
        console.log('Licences issued with a previous key stop being valid in builds with this one.')
        return
    }

    if (command === 'issue') {
        const opts = options(args)
        for (const required of ['private', 'customer', 'seats', 'out']) {
            if (!opts[required]) throw new Error(`Missing --${required}`)
        }
        const seats = Number(opts.seats)
        if (!Number.isInteger(seats) || seats < 1) throw new Error('--seats must be a whole number of at least 1')
        if (opts.expires && !/^\d{4}-\d{2}-\d{2}$/.test(opts.expires)) throw new Error('--expires must look like 2027-12-31')
        const details = {
            id: opts.id ?? `FS-${today().replaceAll('-', '')}-${randomBytes(3).toString('hex').toUpperCase()}`,
            customer: opts.customer,
            seats,
            issuedAt: today(),
            expiresAt: opts.expires ?? null,
        }
        const privateKeyPem = fs.readFileSync(opts.private, 'utf-8')
        const licence = issueLicense(privateKeyPem, details)
        const publicKeyPem = builtInPublicKey()
        if (publicKeyPem && createPublicKey(createPrivateKey(privateKeyPem)).export({ type: 'spki', format: 'pem' }) !== publicKeyPem) {
            throw new Error('This private key does not match the public key built into the app (electron/license-key.ts).')
        }
        fs.writeFileSync(opts.out, licence + '\n', { flag: 'wx' })
        console.log(`Licence ${details.id} for ${details.customer} written to ${opts.out}`)
        return
    }

    if (command === 'inspect') {
        const token = fs.readFileSync(args[0], 'utf-8').trim()
        const [payload, signature] = token.slice(LICENSE_PREFIX.length).split('.')
        console.log(JSON.parse(Buffer.from(payload, 'base64url').toString('utf-8')))
        const publicKeyPem = builtInPublicKey()
        const valid = publicKeyPem !== '' && verify(null, Buffer.from(LICENSE_PREFIX + payload, 'utf-8'),
            createPublicKey(publicKeyPem), Buffer.from(signature, 'base64url'))
        console.log(valid ? 'Signature matches the built-in key.' : 'Signature does NOT match the built-in key.')
        return
    }

    console.log('Usage: node scripts/license.mjs keygen | issue | inspect (see the comment at the top of this file)')
    process.exitCode = command ? 1 : 0
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
    try {
        main(process.argv.slice(2))
    } catch (error) {
        console.error(error.message)
        process.exitCode = 1
    }
}
