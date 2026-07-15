import { TestBed }            from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ProcessService }     from './process.service';
import { environment }        from '../../environments/environment';

describe('ProcessService', () => {
  let service: ProcessService;
  let http:    HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports:   [HttpClientTestingModule],
      providers: [ProcessService]
    });
    service = TestBed.inject(ProcessService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('deve chamar GET /processes para listar processos', () => {
    service.list().subscribe();
    http.expectOne(`${environment.apiUrl}/processes`).flush({ success: true, data: { content: [] } });
  });

  it('deve chamar GET /processes com filtro active=true', () => {
    service.list({ active: true }).subscribe();
    const req = http.expectOne(r => r.url.includes('/processes') && r.params.get('active') === 'true');
    req.flush({ success: true, data: { content: [] } });
  });

  it('deve chamar POST /processes para criar processo', () => {
    const payload = { processNumber: '0001234-55.2020.8.26.0001', courtCode: 'STF' };
    service.create(payload).subscribe();
    const req = http.expectOne(`${environment.apiUrl}/processes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ success: true });
  });

  it('deve chamar DELETE para remover processo', () => {
    service.delete('sub-id-123').subscribe();
    const req = http.expectOne(`${environment.apiUrl}/processes/sub-id-123`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true });
  });
});