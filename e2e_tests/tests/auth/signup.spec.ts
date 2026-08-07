import { test, expect } from '@playwright/test';

test(
  'C98 [ID] 아이디 글자 수 미달',
  {
    annotation: {
      type: 'test_id',
      description: 'C98',
    },
  },
  async ({ page }) => {
    await page.goto('/signup');

    await page.getByRole('textbox', { name: 'ID' }).fill('e2');

    await expect(
      page.getByText('아이디는 3~30자로 입력해주세요.'),
    ).toBeVisible();
  },
);

test(
  'C103 [ID] 아이디 글자 수 초과',
  {
    annotation: {
      type: 'test_id',
      description: 'C103',
    },
  },
  async ({ page }) => {
    await page.goto('/signup');

    const idInput = page.getByRole('textbox', { name: 'ID' });

    await idInput.fill('a'.repeat(31));

    await expect(idInput).toHaveValue('a'.repeat(30));
  },
);

test(
  'C97 [ID] 아이디 중복 체크',
  {
    annotation: {
      type: 'test_id',
      description: 'C97',
    },
  },
  async ({ page }) => {
    await page.goto('/signup');

    await page.getByRole('textbox', { name: 'ID' }).fill('Tester');
    await page
      .getByRole('textbox', { name: 'PASSWORD', exact: true })
      .fill('password123');
    await page
      .getByRole('textbox', { name: 'PASSWORD CONFIRM' })
      .fill('password123');
    await page.getByRole('textbox', { name: 'NICKNAME' }).fill('Tester');
    await page.getByRole('textbox', { name: 'EMAIL' }).fill('test@test.com');

    await page
      .getByRole('button', { name: 'CREATE ACCOUNT ↗' })
      .click();

    await expect(
      page.getByText('이미 사용 중인 아이디입니다.'),
    ).toBeVisible();

    await expect(page).toHaveURL(/\/signup/);
  },
);

test(
  'C99 [Password] Password 글자 수 미달',
  {
    annotation: {
      type: 'test_id',
      description: 'C99',
    },
  },
  async ({ page }) => {
    await page.goto('/signup');

    await page
      .getByRole('textbox', { name: 'PASSWORD', exact: true })
      .fill('passwor');

    await expect(
      page.getByText('비밀번호는 8자 이상 입력해주세요.'),
    ).toBeVisible();
  },
);

test(
  'C100 [Password] Password 확인 불일치',
  {
    annotation: {
      type: 'test_id',
      description: 'C100',
    },
  },
  async ({ page }) => {
    await page.goto('/signup');

    await page
      .getByRole('textbox', { name: 'PASSWORD', exact: true })
      .fill('password123');

    await page
      .getByRole('textbox', { name: 'PASSWORD CONFIRM' })
      .fill('passsword124');

    await expect(
      page.getByText('비밀번호가 일치하지 않습니다.'),
    ).toBeVisible();
  },
);

test(
  'C101 [Nickname] 닉네임 글자 수 미달',
  {
    annotation: {
      type: 'test_id',
      description: 'C101',
    },
  },
  async ({ page }) => {
    await page.goto('/signup');

    await page.getByRole('textbox', { name: 'NICKNAME' }).fill('T');

    await expect(
      page.getByText('닉네임은 2~30자로 입력해주세요.'),
    ).toBeVisible();
  },
);

test(
  'C104 [Nickname] 닉네임 글자 수 초과',
  {
    annotation: {
      type: 'test_id',
      description: 'C104',
    },
  },
  async ({ page }) => {
    await page.goto('/signup');

    const nicknameInput = page.getByRole('textbox', {
      name: 'NICKNAME',
    });

    await nicknameInput.fill('a'.repeat(31));

    await expect(nicknameInput).toHaveValue('a'.repeat(30));
  },
);

test(
  'C102 [Email] 이메일 입력 양식이 틀린 경우',
  {
    annotation: {
      type: 'test_id',
      description: 'C102',
    },
  },
  async ({ page }) => {
    await page.goto('/signup');

    const emailInput = page.getByRole('textbox', { name: 'EMAIL' });
    const errorMessage = page.getByText(
      '올바른 이메일 형식으로 입력해주세요.',
      { exact: true },
    );

    const invalidEmails = [
      ' @gmail.com',
      '@gmail.com',
      'testtest.com',
      'test@gmail',
    ];

    for (const email of invalidEmails) {
      await emailInput.fill(email);
      await expect(errorMessage).toBeVisible();
    }
  },
);

test(
  'C115 [Email] 이메일 중복 체크',
  {
    annotation: {
      type: 'test_id',
      description: 'C115',
    },
  },
  async ({ page }) => {
    await page.goto('/signup');

    await page.getByRole('textbox', { name: 'ID' }).fill('Email_Test');
    await page
      .getByRole('textbox', { name: 'PASSWORD', exact: true })
      .fill('password123');
    await page
      .getByRole('textbox', { name: 'PASSWORD CONFIRM' })
      .fill('password123');
    await page.getByRole('textbox', { name: 'NICKNAME' }).fill('Tester');
    await page.getByRole('textbox', { name: 'EMAIL' }).fill('test@test.com');

    await page
      .getByRole('button', { name: 'CREATE ACCOUNT ↗' })
      .click();

    await expect(
      page.getByText('이미 사용 중인 이메일입니다.'),
    ).toBeVisible();

    await expect(page).toHaveURL(/\/signup/);
  },
);

test(
  'C92 [SignUp] 회원가입 정상 수행',
  {
    annotation: {
      type: 'test_id',
      description: 'C92',
    },
  },
  async ({ page }, testInfo) => {
    const browser = testInfo.project.name.slice(0, 2);
    const unique = `${browser}_${Date.now().toString().slice(-8)}`;

    const user = {
      id: `e2e_test_${unique}`,
      password: 'password123',
      nickname: 'E2E Tester',
      email: `e2e_test_${unique}@example.com`,
    };

    await page.goto('/signup');

    await page.getByRole('textbox', { name: 'ID' }).fill(user.id);
    await page
      .getByRole('textbox', { name: 'PASSWORD', exact: true })
      .fill(user.password);
    await page
      .getByRole('textbox', { name: 'PASSWORD CONFIRM' })
      .fill(user.password);
    await page
      .getByRole('textbox', { name: 'NICKNAME' })
      .fill(user.nickname);
    await page
      .getByRole('textbox', { name: 'EMAIL' })
      .fill(user.email);

    await page
      .getByRole('button', { name: 'CREATE ACCOUNT ↗' })
      .click();

    await expect(page).toHaveURL(/\/my$/);
  },
);