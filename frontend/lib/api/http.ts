export async function readJson<T>(response: Response): Promise<T> {
  try {
    return (await response.json()) as T;
  } catch {
    throw new Error('NOT_JSON');
  }
}

export async function fetchJson<T>(
  url: string,
  init?: RequestInit,
): Promise<{status: number; body: T}> {
  let response: Response;
  try {
    response = await fetch(url, init);
  } catch {
    throw new Error('NETWORK');
  }
  const body = await readJson<T>(response);
  return {status: response.status, body};
}
