#[derive(Clone)]
pub struct SinceDate {
    pub years: i32,
    pub months: i32,
    pub days: i32,
}

impl SinceDate {
    pub fn new(years: i32, months: i32, days: i32) -> Self {
        SinceDate { years, months, days }
    }
}

impl std::fmt::Display for SinceDate {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let m = clamp_month(self.months);
        let d = clamp_day(self.days);
        write!(f, "{}-{:02}-{:02}", self.years, m, d)
    }
}

fn clamp_month(m: i32) -> i32 {
    if m < 1 || m > 12 { 1 } else { m }
}

fn clamp_day(d: i32) -> i32 {
    if d < 1 || d > 30 { 1 } else { d }
}
