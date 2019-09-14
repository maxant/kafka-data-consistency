select *
from CONTRACTS c
join PRODUCTS p on p.CONTRACT_ID = c.ID
join HOME_CONTENTS_PRODUCTS h on h.ID = p.ID
where c.CONTRACTNUMBER = '1568495960031'
and c.FROM_ <= '2019-09-01' and '2019-09-01' <= c.TO_
;

desc CONTRACTS;