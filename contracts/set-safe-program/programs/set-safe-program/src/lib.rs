use anchor_lang::prelude::*;

declare_id!("Hj1rx5gpvzbLvWXz1vxLUkcqnoKrKSfgBVMfpmJ97Hmz");

#[program]
pub mod set_safe_program {
    use super::*;

    pub fn set_safe(ctx: Context<SetSafe>, safe: Pubkey) -> Result<()> {
        let config = &mut ctx.accounts.config;
        config.authority = ctx.accounts.authority.key();
        config.safe = safe;
        config.bump = ctx.bumps.config;
        Ok(())
    }
}

#[derive(Accounts)]
pub struct SetSafe<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(
        init_if_needed,
        payer = authority,
        space = 8 + SafeConfig::INIT_SPACE,
        seeds = [b"safe_config", authority.key().as_ref()],
        bump
    )]
    pub config: Account<'info, SafeConfig>,
    pub system_program: Program<'info, System>,
}

#[account]
#[derive(InitSpace)]
pub struct SafeConfig {
    pub authority: Pubkey,
    pub safe: Pubkey,
    pub bump: u8,
}
