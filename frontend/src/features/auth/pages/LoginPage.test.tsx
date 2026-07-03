import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import LoginPage from './LoginPage';
import { useLogin } from '../../../hooks/auth/useAuth';
import { authApi } from '../../../services/auth/authApi';

// Mock the dependencies
vi.mock('../../../hooks/auth/useAuth', () => ({
  useLogin: vi.fn(),
}));

vi.mock('../../../services/auth/authApi', () => ({
  authApi: {
    forgotPasswordRequest: vi.fn(),
    forgotPasswordVerify: vi.fn(),
    forgotPasswordReset: vi.fn(),
  },
}));

describe('LoginPage', () => {
  const mockMutateAsync = vi.fn();
  const mockUseLogin = useLogin as any;

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseLogin.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
      error: null,
    });
  });

  const setup = () => render(<LoginPage />);

  it('renders login form initially', () => {
    setup();
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByLabelText('Username')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
  });

  it('validates empty login fields', async () => {
    setup();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findAllByText('Required')).toHaveLength(2);
    expect(mockMutateAsync).not.toHaveBeenCalled();
  });

  it('lowercases username input', async () => {
    setup();
    const user = userEvent.setup();
    const usernameInput = screen.getByLabelText('Username');
    await user.type(usernameInput, 'JohnDoe');
    expect(usernameInput).toHaveValue('johndoe');
  });

  it('toggles password visibility', async () => {
    setup();
    const user = userEvent.setup();
    const passwordInput = screen.getByLabelText('Password');
    
    expect(passwordInput).toHaveAttribute('type', 'password');
    
    const toggleButton = screen.getByRole('button', { name: 'Show password' });
    await user.click(toggleButton);
    expect(passwordInput).toHaveAttribute('type', 'text');
    
    const hideButton = screen.getByRole('button', { name: 'Hide password' });
    await user.click(hideButton);
    expect(passwordInput).toHaveAttribute('type', 'password');
  });

  it('submits login successfully (no branches)', async () => {
    setup();
    const user = userEvent.setup();
    
    mockMutateAsync.mockResolvedValueOnce({ data: { status: 'SUCCESS' } });

    await user.type(screen.getByLabelText('Username'), 'testuser');
    await user.type(screen.getByLabelText('Password'), 'password123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith({
        username: 'testuser',
        password: 'password123',
        branchId: null,
      });
    });
  });

  it('shows login API error from hook', () => {
    mockUseLogin.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
      error: { response: { data: { message: 'Invalid credentials' } } },
    });
    setup();
    expect(screen.getByText('Invalid credentials')).toBeInTheDocument();
  });

  it('handles mutateAsync rejection gracefully', async () => {
    setup();
    const user = userEvent.setup();
    
    mockMutateAsync.mockRejectedValueOnce(new Error('Network error'));

    await user.type(screen.getByLabelText('Username'), 'testuser');
    await user.type(screen.getByLabelText('Password'), 'password123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    // The catch block suppresses the error, so we just expect the mutation to have been called
    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalled();
    });
  });

  it('handles multiple branches flow', async () => {
    setup();
    const user = userEvent.setup();
    
    mockMutateAsync.mockResolvedValueOnce({
      data: {
        status: 'MULTIPLE_BRANCHES',
        branches: [{ id: 'b1', name: 'Branch 1' }, { id: 'b2', name: 'Branch 2' }],
      },
    });

    await user.type(screen.getByLabelText('Username'), 'testuser');
    await user.type(screen.getByLabelText('Password'), 'password123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    // Wait for branch selection UI
    expect(await screen.findByText('Select Branch')).toBeInTheDocument();
    
    // Select branch
    const branchSelect = screen.getByLabelText('Branch');
    await user.selectOptions(branchSelect, 'b2');

    // Submit with branch selected
    mockMutateAsync.mockResolvedValueOnce({ data: { status: 'SUCCESS' } });
    await user.click(screen.getByRole('button', { name: 'Confirm & Sign in' }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenLastCalledWith({
        username: 'testuser',
        password: 'password123',
        branchId: 'b2',
      });
    });
    
    // Back to credentials
    await user.click(screen.getByText('Back to credentials'));
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  describe('Forgot Password Flow', () => {
    it('navigates to forgot password and validates email', async () => {
      setup();
      const user = userEvent.setup();
      
      await user.click(screen.getByText('Forgot password?'));
      expect(screen.getByRole('heading', { name: 'Forgot password' })).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'Send OTP' }));
      expect(await screen.findByText('Email is required')).toBeInTheDocument();

      await user.type(screen.getByLabelText('Email address'), 'invalid-email');
      await user.click(screen.getByRole('button', { name: 'Send OTP' }));
      expect(await screen.findByText('Invalid email address')).toBeInTheDocument();
    });

    it('sends OTP and handles API error', async () => {
      setup();
      const user = userEvent.setup();
      
      await user.click(screen.getByText('Forgot password?'));
      
      vi.mocked(authApi.forgotPasswordRequest).mockRejectedValueOnce({
        response: { data: { message: 'User not found' } }
      });

      await user.type(screen.getByLabelText('Email address'), 'test@example.com');
      await user.click(screen.getByRole('button', { name: 'Send OTP' }));

      expect(await screen.findByText('User not found')).toBeInTheDocument();
    });

    it('successfully goes through the entire reset password flow', async () => {
      setup();
      const user = userEvent.setup();
      
      // 1. Request OTP
      await user.click(screen.getByText('Forgot password?'));
      vi.mocked(authApi.forgotPasswordRequest).mockResolvedValueOnce({} as any);
      
      await user.type(screen.getByLabelText('Email address'), 'test@example.com');
      await user.click(screen.getByRole('button', { name: 'Send OTP' }));

      // 2. Verify OTP
      expect(await screen.findByRole('heading', { name: 'Verify OTP' })).toBeInTheDocument();
      expect(screen.getByText('OTP sent successfully to your email.')).toBeInTheDocument();
      
      // Validation error for OTP
      await user.click(screen.getByRole('button', { name: 'Verify Code' }));
      expect(await screen.findByText('OTP must be 6 digits')).toBeInTheDocument();

      // Verify OTP Success
      vi.mocked(authApi.forgotPasswordVerify).mockResolvedValueOnce({} as any);
      await user.type(screen.getByLabelText('Verification Code'), '123456');
      await user.click(screen.getByRole('button', { name: 'Verify Code' }));

      // 3. Reset Password
      expect(await screen.findByRole('heading', { name: 'Choose new password' })).toBeInTheDocument();
      expect(screen.getByText('OTP verified. You can now reset your password.')).toBeInTheDocument();

      // Password mismatch
      await user.type(screen.getByLabelText('New Password'), 'newpass123');
      await user.type(screen.getByLabelText('Confirm Password'), 'differentpass');
      await user.click(screen.getByRole('button', { name: 'Reset Password' }));
      expect(await screen.findByText("Passwords don't match")).toBeInTheDocument();

      // Clear confirm password and fix
      await user.clear(screen.getByLabelText('Confirm Password'));
      await user.type(screen.getByLabelText('Confirm Password'), 'newpass123');

      // Reset password success
      vi.mocked(authApi.forgotPasswordReset).mockResolvedValueOnce({} as any);
      await user.click(screen.getByRole('button', { name: 'Reset Password' }));

      // 4. Back to login screen on success
      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
      expect(screen.getByText('Password reset successfully. Please sign in with your new password.')).toBeInTheDocument();
    });

    it('can back out of verify OTP screen', async () => {
      setup();
      const user = userEvent.setup();
      
      await user.click(screen.getByText('Forgot password?'));
      vi.mocked(authApi.forgotPasswordRequest).mockResolvedValueOnce({} as any);
      await user.type(screen.getByLabelText('Email address'), 'test@example.com');
      await user.click(screen.getByRole('button', { name: 'Send OTP' }));

      expect(await screen.findByRole('heading', { name: 'Verify OTP' })).toBeInTheDocument();
      await user.click(screen.getByText('Back to sign in'));
      expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    });

    it('handles verify OTP API error', async () => {
      setup();
      const user = userEvent.setup();
      
      await user.click(screen.getByText('Forgot password?'));
      vi.mocked(authApi.forgotPasswordRequest).mockResolvedValueOnce({} as any);
      await user.type(screen.getByLabelText('Email address'), 'test@example.com');
      await user.click(screen.getByRole('button', { name: 'Send OTP' }));

      expect(await screen.findByRole('heading', { name: 'Verify OTP' })).toBeInTheDocument();
      
      vi.mocked(authApi.forgotPasswordVerify).mockRejectedValueOnce({
        response: { data: { message: 'Invalid OTP' } }
      });
      await user.type(screen.getByLabelText('Verification Code'), '123456');
      await user.click(screen.getByRole('button', { name: 'Verify Code' }));

      expect(await screen.findByText('Invalid OTP')).toBeInTheDocument();
    });

    it('handles reset password API error', async () => {
      setup();
      const user = userEvent.setup();
      
      // Navigate to Reset Password state directly using a sequence
      await user.click(screen.getByText('Forgot password?'));
      vi.mocked(authApi.forgotPasswordRequest).mockResolvedValueOnce({} as any);
      await user.type(screen.getByLabelText('Email address'), 'test@example.com');
      await user.click(screen.getByRole('button', { name: 'Send OTP' }));

      vi.mocked(authApi.forgotPasswordVerify).mockResolvedValueOnce({} as any);
      await user.type(await screen.findByLabelText('Verification Code'), '123456');
      await user.click(screen.getByRole('button', { name: 'Verify Code' }));

      // Try Reset Password with error
      vi.mocked(authApi.forgotPasswordReset).mockRejectedValueOnce({
        response: { data: { message: 'Reset failed' } }
      });
      
      const newPassInput = await screen.findByLabelText('New Password');
      const confirmPassInput = screen.getByLabelText('Confirm Password');
      await user.type(newPassInput, 'newpass123');
      await user.type(confirmPassInput, 'newpass123');
      
      await user.click(screen.getByRole('button', { name: 'Reset Password' }));

      expect(await screen.findByText('Reset failed')).toBeInTheDocument();
    });
  });

  it('toggles password visibility for reset password flow', async () => {
    setup();
    const user = userEvent.setup();
    
    // Go to reset password flow
    await user.click(screen.getByText('Forgot password?'));
    vi.mocked(authApi.forgotPasswordRequest).mockResolvedValueOnce({} as any);
    await user.type(screen.getByLabelText('Email address'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: 'Send OTP' }));

    vi.mocked(authApi.forgotPasswordVerify).mockResolvedValueOnce({} as any);
    await user.type(await screen.findByLabelText('Verification Code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Verify Code' }));

    const newPassInput = await screen.findByLabelText('New Password');
    const confirmPassInput = screen.getByLabelText('Confirm Password');
    
    // First button is New Password toggle, second is Confirm Password toggle
    const toggleButtons = screen.getAllByRole('button', { name: 'Show password' });
    
    // Toggle new password
    await user.click(toggleButtons[0]);
    expect(newPassInput).toHaveAttribute('type', 'text');
    await user.click(screen.getAllByRole('button', { name: 'Hide password' })[0]);
    expect(newPassInput).toHaveAttribute('type', 'password');

    // Toggle confirm password
    await user.click(screen.getAllByRole('button', { name: 'Show password' })[1]);
    expect(confirmPassInput).toHaveAttribute('type', 'text');
    await user.click(screen.getAllByRole('button', { name: 'Hide password' })[0]);
    expect(confirmPassInput).toHaveAttribute('type', 'password');
  });
});
