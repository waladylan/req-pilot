export * from "./edge.constants";
export * from "./flow-renderer.constants";
export * from "./flow.constants";
export * from "./test-case.constants";
export * from "./workspace.constants";
export * from "./wfm.constants";

export const QUERY_KEYS = {
  REQUIREMENT_FLOW: "requirement-flow",
  TEST_CASES: "test-cases",
} as const;

export const SAMPLE_REQUIREMENTS = [
  {
    label: "Login Flow",
    value: `Feature: User Login

Start
1. User opens the Login screen.
2. User enters username and password.
3. System validates the input.

If required information is missing:
- Show validation error.
- End.

If credentials are valid:
- Authenticate user.
- Generate access token.
- Redirect to Dashboard.
- End.

If credentials are invalid:
- Show "Invalid username or password".
- Allow user to retry.

If retry:
- Return to Login screen.

If user cancels login:
- Return to Home page.
- End.

If authentication succeeds:
- Show login success message.
- End.

If system cannot connect to authentication server:
- Show system error.
- End.`,
  },
  {
    label: "Purchase Request",
    value:
      "User can create a purchase request. Manager approves. If amount > 5000, finance approval is required.",
  },
  {
    label: "Registration Flow",
    value: `Feature: User Registration

1. User opens the registration form.
2. User enters profile information.
3. System validates required fields.
4. If email is approved, create user account.
5. If required information is missing, show validation error.
6. If account creation succeeds, send welcome email and show success message.
7. If account creation fails, show registration failure message.`,
  },
  {
    label: "Checkout Flow",
    value: `Feature: Checkout Order

1. User opens cart.
2. User reviews order summary.
3. System calculates shipping and tax.
4. If cart is valid, proceed to payment.
5. If user cancels checkout, return to cart.
6. If payment succeeds, create order and show receipt.
7. If payment fails, show payment error and keep order pending.`,
  },
  {
    label: "Delete Product",
    value: `Feature: Delete Product

1. Admin opens product list.
2. Admin selects a product.
3. System shows delete confirmation dialog.
4. If admin confirms deletion, delete product.
5. If admin cancels deletion, keep product unchanged.
6. If deletion succeeds, show success notification.
7. If deletion fails, show delete error message.`,
  },
  {
    label: "Password Reset",
    value: `Feature: Password Reset

1. User opens forgot password page.
2. User enters account email.
3. System checks whether the account exists.
4. If account is valid, send password reset link.
5. If account is not found, show account not found message.
6. If email delivery succeeds, show reset instructions.
7. If email delivery fails, show email delivery error.`,
  },
  {
    label: "Approval Process",
    value: `Feature: Expense Approval Workflow

1. Employee submits expense request.
2. Manager reviews request details.
3. System checks policy rules.
4. If manager approves request, mark expense as approved.
5. If manager rejects request, return request to employee.
6. If policy validation succeeds, send approval notification.
7. If policy validation fails, show policy violation error.`,
  },
] as const;
