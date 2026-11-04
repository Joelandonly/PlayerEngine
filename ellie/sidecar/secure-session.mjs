/**
 * Keep an OAuth session usable even when the host cannot securely persist it.
 * Never fall back to plaintext or reversible obfuscation.
 * The caller must keep the returned connection in RAM and not log its content.
 */
export function storeSecureSession(value, {isWindows, seal, writeEncrypted, warn = () => {}}) {
  if (!isWindows) {
    warn('No supported Windows credential store on this host. ChatGPT login will work only until this bridge closes; no tokens were saved.');
    return {connection: value, persistence: 'memory-only'};
  }
  try {
    const ciphertext = seal(JSON.stringify(value));
    if (typeof ciphertext !== 'string' || !ciphertext.trim()) {
      throw new Error('credential store returned an empty encrypted payload');
    }
    writeEncrypted(ciphertext);
    return {connection: value, persistence: 'encrypted'};
  } catch (error) {
    // Deliberately do not include error.message: Windows/PowerShell errors can
    // include machine-specific paths or source context.
    warn('Windows credential encryption failed. Authorization is still active for THIS bridge session only; no plaintext token file was created. You will need to type login again after restarting.');
    return {connection: value, persistence: 'memory-only'};
  }
}
