/**
 * Validation of the search engine address, shared by the Settings page and the
 * main process (which enforces it). No Node or DOM dependencies.
 */

const LOOPBACK_HOSTS: ReadonlySet<string> = new Set(['localhost', '127.0.0.1', '[::1]']);
const INDEX_NAME_PATTERN = /^[a-z0-9][a-z0-9_-]{0,254}$/;

/** Only a search engine on this machine is allowed: document text must never leave it. */
export function isLoopbackHttpUrl(value: unknown): value is string {
    if (typeof value !== 'string') return false;
    try {
        const url = new URL(value);
        return (url.protocol === 'http:' || url.protocol === 'https:')
            && LOOPBACK_HOSTS.has(url.hostname)
            && url.username === ''
            && url.password === ''
            && (url.pathname === '/' || url.pathname === '');
    } catch {
        return false;
    }
}

export function isValidIndexName(value: unknown): value is string {
    return typeof value === 'string' && INDEX_NAME_PATTERN.test(value);
}
