import { TestBed }                    from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RouterTestingModule }        from '@angular/router/testing';
import { AuthService }                from './auth.service';
import { StorageService }             from './storage.service';
import { environment }                from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let http:    HttpTestingController;
  let storage: jasmine.SpyObj<StorageService>;

  beforeEach(() => {
    storage = jasmine.createSpyObj('StorageService', [
      'getAccessToken', 'getRefreshToken', 'saveTokens', 'clearTokens', 'set', 'get'
    ]);

    TestBed.configureTestingModule({
      imports:   [HttpClientTestingModule, RouterTestingModule],
      providers: [
        AuthService,
        { provide: StorageService, useValue: storage }
      ]
    });

    service = TestBed.inject(AuthService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('deve retornar false para isLoggedIn quando sem token', () => {
    storage.getAccessToken.and.returnValue(null);
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('deve salvar tokens e atualizar currentUser após login', () => {
    const mockResp = {
      success: true,
      data: {
        accessToken: 'access', refreshToken: 'refresh', expiresIn: 900,
        tokenType: 'Bearer',
        user: { id: '1', name: 'Teste', email: 'teste@teste.com', plan: 'GRATUITO', planDisplay: 'Gratuito' }
      }
    };

    service.login({ email: 'teste@teste.com', password: 'Senha@123' }).subscribe(resp => {
      expect(resp.success).toBeTrue();
      expect(service.currentUser()?.email).toBe('teste@teste.com');
    });

    const req = http.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(mockResp);

    expect(storage.saveTokens).toHaveBeenCalledWith('access', 'refresh');
  });

  it('deve limpar tokens e currentUser no logout', () => {
    storage.getRefreshToken.and.returnValue('refresh-token');

    service.logout();

    http.expectOne(`${environment.apiUrl}/auth/logout`).flush({ success: true });
    expect(storage.clearTokens).toHaveBeenCalled();
  });
});