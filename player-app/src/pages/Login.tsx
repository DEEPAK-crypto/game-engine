import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { Gamepad2, Eye, EyeOff, Loader2, UserPlus, LogIn } from 'lucide-react';
import { useAuth } from '@/stores/auth';
import type { LoginRequest, RegisterRequest } from '@/types';

type AuthMode = 'login' | 'register';

interface RegisterFormData extends RegisterRequest {
  confirmPassword: string;
}

export function Login() {
  const navigate = useNavigate();
  const { login, register: registerUser } = useAuth();
  const [mode, setMode] = useState<AuthMode>('login');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const loginForm = useForm<LoginRequest>();
  const registerForm = useForm<RegisterFormData>();

  const onLogin = async (data: LoginRequest) => {
    setError(null);
    setIsLoading(true);

    try {
      await login(data);
      navigate('/');
    } catch {
      setError('Invalid username or password');
    } finally {
      setIsLoading(false);
    }
  };

  const onRegister = async (data: RegisterFormData) => {
    setError(null);

    if (data.password !== data.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    setIsLoading(true);

    try {
      await registerUser({
        username: data.username,
        password: data.password,
        displayName: data.displayName,
      });
      navigate('/');
    } catch {
      setError('Registration failed. Username may already be taken.');
    } finally {
      setIsLoading(false);
    }
  };

  const toggleMode = () => {
    setMode(mode === 'login' ? 'register' : 'login');
    setError(null);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="bg-white rounded-2xl shadow-xl p-8">
          {/* Header */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 bg-primary-100 rounded-full mb-4">
              <Gamepad2 className="w-8 h-8 text-primary-600" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900">Trivia Game</h1>
            <p className="text-gray-500 mt-1">
              {mode === 'login' ? 'Welcome back!' : 'Create your account'}
            </p>
          </div>

          {/* Mode Toggle */}
          <div className="flex mb-6 bg-gray-100 rounded-lg p-1">
            <button
              type="button"
              onClick={() => setMode('login')}
              className={`
                flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors
                ${mode === 'login'
                  ? 'bg-white text-gray-900 shadow-sm'
                  : 'text-gray-600 hover:text-gray-900'}
              `}
            >
              <LogIn className="w-4 h-4 inline-block mr-2" />
              Sign In
            </button>
            <button
              type="button"
              onClick={() => setMode('register')}
              className={`
                flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors
                ${mode === 'register'
                  ? 'bg-white text-gray-900 shadow-sm'
                  : 'text-gray-600 hover:text-gray-900'}
              `}
            >
              <UserPlus className="w-4 h-4 inline-block mr-2" />
              Register
            </button>
          </div>

          {/* Error Message */}
          {error && (
            <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
              <p className="text-sm text-red-600">{error}</p>
            </div>
          )}

          {/* Login Form */}
          {mode === 'login' && (
            <form onSubmit={loginForm.handleSubmit(onLogin)} className="space-y-6">
              <div>
                <label
                  htmlFor="login-username"
                  className="block text-sm font-medium text-gray-700 mb-1"
                >
                  Username
                </label>
                <input
                  id="login-username"
                  type="text"
                  autoComplete="username"
                  {...loginForm.register('username', {
                    required: 'Username is required',
                    minLength: {
                      value: 3,
                      message: 'Username must be at least 3 characters',
                    },
                  })}
                  className={`
                    w-full px-4 py-3 rounded-lg border bg-gray-50
                    focus:bg-white focus:border-primary-500
                    transition-colors duration-200
                    ${loginForm.formState.errors.username ? 'border-red-300' : 'border-gray-200'}
                  `}
                  placeholder="Enter your username"
                />
                {loginForm.formState.errors.username && (
                  <p className="mt-1 text-sm text-red-500">
                    {loginForm.formState.errors.username.message}
                  </p>
                )}
              </div>

              <div>
                <label
                  htmlFor="login-password"
                  className="block text-sm font-medium text-gray-700 mb-1"
                >
                  Password
                </label>
                <div className="relative">
                  <input
                    id="login-password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    {...loginForm.register('password', {
                      required: 'Password is required',
                    })}
                    className={`
                      w-full px-4 py-3 rounded-lg border bg-gray-50
                      focus:bg-white focus:border-primary-500
                      transition-colors duration-200
                      ${loginForm.formState.errors.password ? 'border-red-300' : 'border-gray-200'}
                    `}
                    placeholder="Enter your password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {showPassword ? (
                      <EyeOff className="w-5 h-5" />
                    ) : (
                      <Eye className="w-5 h-5" />
                    )}
                  </button>
                </div>
                {loginForm.formState.errors.password && (
                  <p className="mt-1 text-sm text-red-500">
                    {loginForm.formState.errors.password.message}
                  </p>
                )}
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className={`
                  w-full py-3 px-4 rounded-lg font-medium text-white
                  bg-primary-600 hover:bg-primary-700
                  focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2
                  disabled:opacity-50 disabled:cursor-not-allowed
                  transition-colors duration-200
                  flex items-center justify-center gap-2
                `}
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-5 h-5 animate-spin" />
                    Signing in...
                  </>
                ) : (
                  'Sign In'
                )}
              </button>
            </form>
          )}

          {/* Register Form */}
          {mode === 'register' && (
            <form onSubmit={registerForm.handleSubmit(onRegister)} className="space-y-6">
              <div>
                <label
                  htmlFor="register-username"
                  className="block text-sm font-medium text-gray-700 mb-1"
                >
                  Username
                </label>
                <input
                  id="register-username"
                  type="text"
                  autoComplete="username"
                  {...registerForm.register('username', {
                    required: 'Username is required',
                    minLength: {
                      value: 3,
                      message: 'Username must be at least 3 characters',
                    },
                    pattern: {
                      value: /^[a-zA-Z0-9_]+$/,
                      message: 'Username can only contain letters, numbers, and underscores',
                    },
                  })}
                  className={`
                    w-full px-4 py-3 rounded-lg border bg-gray-50
                    focus:bg-white focus:border-primary-500
                    transition-colors duration-200
                    ${registerForm.formState.errors.username ? 'border-red-300' : 'border-gray-200'}
                  `}
                  placeholder="Choose a username"
                />
                {registerForm.formState.errors.username && (
                  <p className="mt-1 text-sm text-red-500">
                    {registerForm.formState.errors.username.message}
                  </p>
                )}
              </div>

              <div>
                <label
                  htmlFor="register-displayName"
                  className="block text-sm font-medium text-gray-700 mb-1"
                >
                  Display Name
                </label>
                <input
                  id="register-displayName"
                  type="text"
                  {...registerForm.register('displayName', {
                    required: 'Display name is required',
                    minLength: {
                      value: 2,
                      message: 'Display name must be at least 2 characters',
                    },
                  })}
                  className={`
                    w-full px-4 py-3 rounded-lg border bg-gray-50
                    focus:bg-white focus:border-primary-500
                    transition-colors duration-200
                    ${registerForm.formState.errors.displayName ? 'border-red-300' : 'border-gray-200'}
                  `}
                  placeholder="How should we call you?"
                />
                {registerForm.formState.errors.displayName && (
                  <p className="mt-1 text-sm text-red-500">
                    {registerForm.formState.errors.displayName.message}
                  </p>
                )}
              </div>

              <div>
                <label
                  htmlFor="register-password"
                  className="block text-sm font-medium text-gray-700 mb-1"
                >
                  Password
                </label>
                <input
                  id="register-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  {...registerForm.register('password', {
                    required: 'Password is required',
                    minLength: {
                      value: 6,
                      message: 'Password must be at least 6 characters',
                    },
                  })}
                  className={`
                    w-full px-4 py-3 rounded-lg border bg-gray-50
                    focus:bg-white focus:border-primary-500
                    transition-colors duration-200
                    ${registerForm.formState.errors.password ? 'border-red-300' : 'border-gray-200'}
                  `}
                  placeholder="Create a password"
                />
                {registerForm.formState.errors.password && (
                  <p className="mt-1 text-sm text-red-500">
                    {registerForm.formState.errors.password.message}
                  </p>
                )}
              </div>

              <div>
                <label
                  htmlFor="register-confirmPassword"
                  className="block text-sm font-medium text-gray-700 mb-1"
                >
                  Confirm Password
                </label>
                <input
                  id="register-confirmPassword"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  {...registerForm.register('confirmPassword', {
                    required: 'Please confirm your password',
                  })}
                  className={`
                    w-full px-4 py-3 rounded-lg border bg-gray-50
                    focus:bg-white focus:border-primary-500
                    transition-colors duration-200
                    ${registerForm.formState.errors.confirmPassword ? 'border-red-300' : 'border-gray-200'}
                  `}
                  placeholder="Confirm your password"
                />
                {registerForm.formState.errors.confirmPassword && (
                  <p className="mt-1 text-sm text-red-500">
                    {registerForm.formState.errors.confirmPassword.message}
                  </p>
                )}
              </div>

              <div className="flex items-center">
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="text-sm text-gray-600 hover:text-gray-900"
                >
                  {showPassword ? 'Hide' : 'Show'} passwords
                </button>
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className={`
                  w-full py-3 px-4 rounded-lg font-medium text-white
                  bg-primary-600 hover:bg-primary-700
                  focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2
                  disabled:opacity-50 disabled:cursor-not-allowed
                  transition-colors duration-200
                  flex items-center justify-center gap-2
                `}
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-5 h-5 animate-spin" />
                    Creating account...
                  </>
                ) : (
                  'Create Account'
                )}
              </button>
            </form>
          )}

          {/* Toggle Link */}
          <p className="mt-6 text-center text-sm text-gray-500">
            {mode === 'login' ? (
              <>
                Don't have an account?{' '}
                <button
                  type="button"
                  onClick={toggleMode}
                  className="text-primary-600 hover:text-primary-700 font-medium"
                >
                  Sign up
                </button>
              </>
            ) : (
              <>
                Already have an account?{' '}
                <button
                  type="button"
                  onClick={toggleMode}
                  className="text-primary-600 hover:text-primary-700 font-medium"
                >
                  Sign in
                </button>
              </>
            )}
          </p>
        </div>
      </div>
    </div>
  );
}
