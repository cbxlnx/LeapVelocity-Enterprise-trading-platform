import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { handleApiError } from './api-error';

describe('handleApiError', () => {
  it('reports an unreachable API for status zero', async () => {
    const error = new HttpErrorResponse({ status: 0, statusText: 'Network Error' });

    await expect(firstValueFrom(handleApiError('load data')(error))).rejects.toThrow(
      'Unable to load data: the API is unreachable.'
    );
  });

  it('uses the API message when one is provided', async () => {
    const error = new HttpErrorResponse({
      status: 422,
      statusText: 'Unprocessable Entity',
      error: { message: 'Invalid symbol' },
    });

    await expect(firstValueFrom(handleApiError('load data')(error))).rejects.toThrow(
      'Unable to load data. Invalid symbol'
    );
  });

  it('describes rejected client requests without an API message', async () => {
    const error = new HttpErrorResponse({ status: 400, statusText: 'Bad Request' });

    await expect(firstValueFrom(handleApiError('submit data')(error))).rejects.toThrow(
      'Unable to submit data. The request was rejected (400).'
    );
  });

  it('describes server failures without an API message', async () => {
    const error = new HttpErrorResponse({ status: 503, statusText: 'Unavailable' });

    await expect(firstValueFrom(handleApiError('load data')(error))).rejects.toThrow(
      'Unable to load data. The server returned an error (503).'
    );
  });

  it('uses the base operation message for other statuses', async () => {
    const error = new HttpErrorResponse({ status: 302, statusText: 'Found' });

    await expect(firstValueFrom(handleApiError('load data')(error))).rejects.toThrow(
      'Unable to load data.'
    );
  });
});
