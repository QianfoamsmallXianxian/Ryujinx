using Ryujinx.HLE.HOS.Services.Account.Acc;
using System;
using System.Linq;

namespace LibKenjinx
{
    public static partial class LibKenjinx
    {
        public static string GetOpenedUser()
        {
            UserProfile? lastProfile = SwitchDevice?.AccountManager?.LastOpenedUser;

            return lastProfile?.UserId.ToString() ?? "";
        }

        public static string GetUserPicture(string userId)
        {
            UserId uid = new(userId);

            UserProfile? user = SwitchDevice?.AccountManager?.GetAllUsers().FirstOrDefault(x => x.UserId == uid);

            if (user == null)
                return "";

            byte[]? pic = user.Image;

            return pic != null ? Convert.ToBase64String(pic) : "";
        }

        public static void SetUserPicture(string userId, string picture)
        {
            UserId uid = new(userId);

            SwitchDevice?.AccountManager?.SetUserImage(uid, Convert.FromBase64String(picture));
        }

        public static string GetUserName(string userId)
        {
            UserId uid = new(userId);

            UserProfile? user = SwitchDevice?.AccountManager?.GetAllUsers().FirstOrDefault(x => x.UserId == uid);

            return user?.Name ?? "";
        }

        public static void SetUserName(string userId, string name)
        {
            UserId uid = new(userId);

            SwitchDevice?.AccountManager?.SetUserName(uid, name);
        }

        public static string[] GetAllUsers()
        {
            return SwitchDevice?.AccountManager?.GetAllUsers().Select(x => x.UserId.ToString()).ToArray() ??
                   [];
        }

        public static void AddUser(string userName, string picture)
        {
            SwitchDevice?.AccountManager?.AddUser(userName, Convert.FromBase64String(picture));
        }

        public static void DeleteUser(string userId)
        {
            UserId uid = new(userId);
            SwitchDevice?.AccountManager?.DeleteUser(uid);
        }

        public static void OpenUser(string userId)
        {
            UserId uid = new(userId);
            SwitchDevice?.AccountManager?.OpenUser(uid);
        }

        public static void CloseUser(string userId)
        {
            UserId uid = new(userId);
            SwitchDevice?.AccountManager?.CloseUser(uid);
        }
    }
}
