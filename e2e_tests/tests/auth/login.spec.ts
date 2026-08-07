import { test, expect } from '@playwright/test';

test(
  'C95 [Login] 아이디를 잘못 입력한 경우',
  {
    annotation: {
      type: 'test_id',
      description: 'C95',
    },
  },
  async ({ page }) => {
    await page.goto('/login');

    await page.getByRole('textbox', { name: 'ID' }).fill('WrongId');
    await page
      .getByRole('textbox', { name: 'PASSWORD', exact: true })
      .fill('TestTest');

    await page.getByRole('button', { name: 'LOG IN ↗' }).click();

    await expect(
      page.getByText('아이디 또는 비밀번호가 올바르지 않습니다'),
    ).toBeVisible();

    await expect(page).toHaveURL(/\/login/);
  },
);

test(
  'C96 [Login] Password를 잘못 입력한 경우',
  {
    annotation: {
      type: 'test_id',
      description: 'C96',
    },
  },
  async ({ page }) => {
    await page.goto('/login');

    await page.getByRole('textbox', { name: 'ID' }).fill('Tester');
    await page
      .getByRole('textbox', { name: 'PASSWORD', exact: true })
      .fill('WrongPassword');

    await page.getByRole('button', { name: 'LOG IN ↗' }).click();

    await expect(
      page.getByText('아이디 또는 비밀번호가 올바르지 않습니다'),
    ).toBeVisible();

    await expect(page).toHaveURL(/\/login/);
  },
);

test(
  'C93 [Login] 로그인 정상 수행',
  {
    annotation: {
      type: 'test_id',
      description: 'C93',
    },
  },
  async ({ page }) => {
    await page.goto('/login');

    await page.getByRole('textbox', { name: 'ID' }).fill('Tester');
    await page
      .getByRole('textbox', { name: 'PASSWORD', exact: true })
      .fill('password123');

    await page.getByRole('button', { name: 'LOG IN ↗' }).click();

    await expect(page).toHaveURL(/\/my$/);
  },
);