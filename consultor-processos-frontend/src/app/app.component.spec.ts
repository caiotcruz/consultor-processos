import { TestBed }              from '@angular/core/testing';
import { RouterTestingModule }  from '@angular/router/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { IonicModule }          from '@ionic/angular';
import { AppComponent }         from './app.component';
import { AuthService }          from './core/services/auth.service';
import { PushNotificationService } from './services/push-notification.service';

describe('AppComponent', () => {
  let authSpy: jasmine.SpyObj<AuthService>;
  let pushSpy: jasmine.SpyObj<PushNotificationService>;

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj('AuthService', ['isLoggedIn'], {
      isLoggedIn: jasmine.createSpy().and.returnValue(false)
    });
    pushSpy = jasmine.createSpyObj('PushNotificationService', ['initialize']);
    pushSpy.initialize.and.returnValue(Promise.resolve());

    await TestBed.configureTestingModule({
      imports: [
        IonicModule.forRoot(),
        RouterTestingModule,
        HttpClientTestingModule,
        AppComponent
      ],
      providers: [
        { provide: AuthService,             useValue: authSpy },
        { provide: PushNotificationService, useValue: pushSpy }
      ]
    }).compileComponents();
  });

  it('deve criar o componente', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app     = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('deve renderizar ion-app e ion-router-outlet', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('ion-app')).toBeTruthy();
    expect(el.querySelector('ion-router-outlet')).toBeTruthy();
  });
});